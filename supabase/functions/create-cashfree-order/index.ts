// supabase/functions/create-cashfree-order/index.ts
// Creates a Cashfree payment order with 100% split to the teacher vendor.
// Returns payment_session_id used by the Android SDK to launch checkout.
// ⚠️  Secret keys are NEVER sent to the Android app.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.3"

const CASHFREE_APP_ID     = Deno.env.get("CASHFREE_APP_ID")     || ""
const CASHFREE_SECRET_KEY = Deno.env.get("CASHFREE_SECRET_KEY") || ""
const CASHFREE_ENV        = Deno.env.get("CASHFREE_ENV")        || "TEST"

const CASHFREE_BASE_URL = CASHFREE_ENV === "PROD"
  ? "https://api.cashfree.com"
  : "https://sandbox.cashfree.com"

const corsHeaders = {
  "Access-Control-Allow-Origin":  "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders })
  }

  try {
    // ── 1. Auth check ─────────────────────────────────────────────────────
    const authHeader = req.headers.get("Authorization")
    if (!authHeader) throw new Error("Missing Authorization header")

    const token = authHeader.replace("Bearer ", "")

    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_ANON_KEY") ?? ""
    )

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token)
    if (authError || !user) throw new Error("Unauthorized")

    // ── 2. Parse request body ─────────────────────────────────────────────
    const { student_id, teacher_id, amount, month_key } = await req.json()

    if (!student_id || !teacher_id || !amount) {
      throw new Error("Missing required parameters: student_id, teacher_id, amount")
    }

    const amountInINR = parseInt(amount, 10)
    if (isNaN(amountInINR) || amountInINR < 1) {
      throw new Error("Invalid amount")
    }

    // ── 3. Fetch teacher's Cashfree vendor ID ─────────────────────────────
    const { data: teacher, error: teacherError } = await supabaseClient
      .from("teachers")
      .select("cashfree_vendor_id, vendor_status, academy_name")
      .eq("id", teacher_id)
      .single()

    if (teacherError || !teacher?.cashfree_vendor_id) {
      throw new Error("Teacher has not set up Cashfree payments. Please complete payment setup first.")
    }

    if (teacher.vendor_status !== "ACTIVE") {
      throw new Error(`Payment setup incomplete. Vendor status: ${teacher.vendor_status}. Please wait for KYC approval.`)
    }

    // Create service client to bypass RLS for fetching crucial IDs
    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL")              ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    // ── 4. Fetch student details ──────────────────────────────────────────
    const { data: studentProfile } = await serviceClient
      .from("profiles")
      .select("full_name, email")
      .eq("id", student_id)
      .single()

    const { data: enrollment } = await serviceClient
      .from("enrollments")
      .select("id, whatsapp_number")
      .eq("student_id", student_id)
      .eq("teacher_id", teacher_id)
      .single()

    // ── 5. Generate unique Cashfree order ID ─────────────────────────────
    const currentMonth = month_key || new Date().toISOString().substring(0, 7)
    const cashfreeOrderId = `fm_${student_id.substring(0, 8)}_${Date.now()}`

    // ── 6. Create Cashfree order with 100% split to teacher ───────────────
    // PLATFORM_COMMISSION = 0 (future: change this env var to enable commission)
    const platformCommissionPercent = parseFloat(Deno.env.get("PLATFORM_COMMISSION_PERCENT") || "0")
    const teacherAmount = Math.round(amountInINR * (1 - platformCommissionPercent / 100))

    const orderPayload = {
      order_id:       cashfreeOrderId,
      order_amount:   amountInINR,
      order_currency: "INR",
      customer_details: {
        customer_id:    student_id,
        customer_name:  studentProfile?.full_name  || "Student",
        customer_email: studentProfile?.email       || `student_${student_id}@feesmanager.app`,
        customer_phone: enrollment?.whatsapp_number || "9999999999",
      },
      order_meta: {
        notify_url: `${Deno.env.get("SUPABASE_URL")}/functions/v1/cashfree-payment-webhook`,
      },
      order_note: `Fees payment for ${currentMonth}`,
      // Split 100% to teacher vendor at order creation time
      order_splits: [
        {
          vendor_id: teacher.cashfree_vendor_id,
          amount:    teacherAmount,
        },
      ],
    }

    console.log("Creating Cashfree order:", cashfreeOrderId, "amount:", amountInINR)

    const cfResponse = await fetch(`${CASHFREE_BASE_URL}/pg/orders`, {
      method:  "POST",
      headers: {
        "Content-Type":    "application/json",
        "x-client-id":     CASHFREE_APP_ID,
        "x-client-secret": CASHFREE_SECRET_KEY,
        "x-api-version":   "2023-08-01",
      },
      body: JSON.stringify(orderPayload),
    })

    const cfData = await cfResponse.json()
    console.log("Cashfree order response:", JSON.stringify(cfData))

    if (!cfResponse.ok) {
      const errMsg = cfData?.message || JSON.stringify(cfData)
      throw new Error(`Cashfree Order Error: ${errMsg}`)
    }

    const paymentSessionId = cfData.payment_session_id
    if (!paymentSessionId) {
      throw new Error("No payment_session_id returned from Cashfree")
    }

    // ── 7. Store order in payment_orders table ────────────────────────────
    const { error: insertError } = await serviceClient
      .from("payment_orders")
      .insert({
        cashfree_order_id:  cashfreeOrderId,
        payment_session_id: paymentSessionId,
        enrollment_id:      enrollment?.id      || null,
        student_id:         student_id,
        teacher_id:         teacher_id,
        amount:             amountInINR,         // stored in INR
        currency:           "INR",
        status:             "created",
        month_key:          currentMonth,
        payment_provider:   "cashfree",
      })

    if (insertError) {
      console.error("Order insert error:", insertError)
      throw insertError
    }

    return new Response(
      JSON.stringify({
        cashfree_order_id:   cashfreeOrderId,
        payment_session_id:  paymentSessionId,
        amount:              amountInINR,
        academy_name:        teacher.academy_name,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )

  } catch (error: any) {
    console.error("create-cashfree-order error:", error)
    return new Response(
      JSON.stringify({ error: error?.message || "Order creation failed" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }
})
