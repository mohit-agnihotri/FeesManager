// supabase/functions/create-cashfree-vendor/index.ts
// Creates a Cashfree Easy Split vendor for a teacher.
// Called from CashfreeOnboardingActivity via CashfreeRepository.
// ⚠️  CASHFREE_APP_ID and CASHFREE_SECRET_KEY are stored ONLY here in env vars.
//     They are NEVER sent to the Android app.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.3"

const CASHFREE_APP_ID     = Deno.env.get("CASHFREE_APP_ID")     || ""
const CASHFREE_SECRET_KEY = Deno.env.get("CASHFREE_SECRET_KEY") || ""
const CASHFREE_ENV        = Deno.env.get("CASHFREE_ENV")        || "TEST" // "TEST" or "PROD"

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
    const authorization = req.headers.get("Authorization")
    if (!authorization) throw new Error("Missing Authorization header")

    const token = authorization.replace("Bearer ", "")

    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_ANON_KEY") ?? ""
    )

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token)
    if (authError || !user) throw new Error("Unauthorized")

    // ── 2. Parse request body ─────────────────────────────────────────────
    const {
      account_name,    // Account holder name
      account_number,  // Bank account number
      ifsc,            // IFSC code
      pan_number,      // PAN number — REQUIRED for Individual KYC
      phone,           // Teacher's phone number
      email,           // Teacher's email (from auth)
    } = await req.json()

    if (!account_name || !account_number || !ifsc || !pan_number || !phone) {
      throw new Error("Missing required fields: account_name, account_number, ifsc, pan_number, phone")
    }

    if (ifsc.length !== 11) throw new Error("IFSC must be 11 characters")
    if (pan_number.length !== 10) throw new Error("PAN must be 10 characters")

    // ── 3. Create Cashfree Easy Split Vendor ─────────────────────────────
    // vendor_id must be unique. If a previous one failed, we must use a new one.
    const uniqueSuffix = Math.floor(Math.random() * 100000).toString()
    const vendorId = `teacher_${user.id.replace(/-/g, "").substring(0, 15)}_${uniqueSuffix}`

    const createPayload = {
      vendor_id:        vendorId,
      status:           "ACTIVE",
      name:             account_name,
      email:            email || user.email || `teacher_${user.id}@feesmanager.app`,
      phone:            phone,
      verify_account:   false,
      dashboard_access: false,
      schedule_option:  1,
      bank: {
        account_number: account_number,
        account_holder: account_name,
        ifsc:           ifsc.toUpperCase(),
      },
      kyc_details: {
        account_type:  "Individual",
        business_type: "Education",
        pan:           pan_number.toUpperCase(),
      },
    }

    console.log("Creating NEW Cashfree vendor for teacher:", vendorId)

    const cfResponse = await fetch(`${CASHFREE_BASE_URL}/pg/easy-split/vendors`, {
      method:  "POST",
      headers: {
        "Content-Type":    "application/json",
        "x-client-id":     CASHFREE_APP_ID,
        "x-client-secret": CASHFREE_SECRET_KEY,
        "x-api-version":   "2023-08-01",
      },
      body: JSON.stringify(createPayload),
    })

    const cfData = await cfResponse.json()
    console.log("Cashfree vendor response:", JSON.stringify(cfData))

    if (!cfResponse.ok) {
      const errMsg = cfData?.message || cfData?.error || JSON.stringify(cfData)
      throw new Error(`Cashfree Error: ${errMsg}`)
    }

    let finalVendorStatus = cfData.status || "IN_BENE_CREATION"
    let finalKycStatus    = "IN_REVIEW"

    // ── Sandbox Override to unblock developer testing ──────────────────────
    if (CASHFREE_ENV === "TEST") {
      finalVendorStatus = "ACTIVE"
      finalKycStatus    = "VERIFIED"
    }

    // ── 4. Store vendor info in Supabase ──────────────────────────────────
    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL")              ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    const { error: updateError } = await serviceClient
      .from("teachers")
      .update({
        cashfree_vendor_id:  vendorId,
        vendor_status:       finalVendorStatus,
        kyc_status:          finalKycStatus,
        bank_account_name:   account_name,
        bank_account_number: account_number,
        bank_ifsc:           ifsc.toUpperCase(),
        pan_number:          pan_number.toUpperCase(),
        teacher_phone:       phone,
      })
      .eq("id", user.id)

    if (updateError) {
      console.error("Supabase update error:", updateError)
      throw updateError
    }

    return new Response(
      JSON.stringify({
        success:           true,
        vendor_id:         vendorId,
        vendor_status:     finalVendorStatus,
        kyc_status:        finalKycStatus,
        message:           "Vendor created.",
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )

  } catch (error: any) {
    console.error("create-cashfree-vendor error:", error)
    return new Response(
      JSON.stringify({ error: error?.message || "Unknown error" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }
})
