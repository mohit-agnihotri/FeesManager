// supabase/functions/verify-cashfree-payment/index.ts
//
// Called by the Android app AFTER Cashfree SDK confirms payment success.
//
// PAYMENT ALLOCATION — OLDEST MONTH FIRST:
//   1. Fetch all pending/partial fee_records for this enrollment, oldest first
//   2. Apply payment to oldest pending month, then next, etc.
//   3. Any remaining after all pending months cleared → advance_balance
//
// TABLE UPDATES:
//   payment_orders → "paid"
//   payments       → insert row (single entry for the total payment)
//   fee_records    → update each affected month's paid_amount & status
//   enrollments    → update advance_balance if any remainder

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

    // ── 2. Parse request ──────────────────────────────────────────────────
    const { cashfree_order_id } = await req.json()
    if (!cashfree_order_id) throw new Error("Missing cashfree_order_id")

    console.log("Verifying Cashfree payment for order:", cashfree_order_id)

    // ── 3. Service client (bypasses RLS) ──────────────────────────────────
    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL")              ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    // ── 4. Look up order in our DB ────────────────────────────────────────
    const { data: order, error: orderError } = await serviceClient
      .from("payment_orders")
      .select("*")
      .eq("cashfree_order_id", cashfree_order_id)
      .single()

    if (orderError || !order) {
      throw new Error(`Order not found: ${cashfree_order_id}`)
    }

    // Already processed? Return success immediately
    if (order.status === "paid") {
      console.log("Order already paid:", cashfree_order_id)
      return new Response(
        JSON.stringify({ status: "already_paid" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      )
    }

    // ── 5. Verify payment with Cashfree API ───────────────────────────────
    const cfResponse = await fetch(
      `${CASHFREE_BASE_URL}/pg/orders/${cashfree_order_id}/payments`,
      {
        method:  "GET",
        headers: {
          "x-client-id":     CASHFREE_APP_ID,
          "x-client-secret": CASHFREE_SECRET_KEY,
          "x-api-version":   "2023-08-01",
        },
      }
    )

    const cfPayments = await cfResponse.json()
    console.log("Cashfree payment verification response:", JSON.stringify(cfPayments))

    // Find the successful payment
    let successfulPayment: any = null

    if (Array.isArray(cfPayments)) {
      successfulPayment = cfPayments.find(
        (p: any) => p.payment_status === "SUCCESS"
      )
    }

    // In TEST/Sandbox mode, if the SDK said success, trust it
    if (!successfulPayment && CASHFREE_ENV === "TEST") {
      console.log("Sandbox mode: SDK confirmed success, proceeding without Cashfree API confirmation")
      successfulPayment = {
        cf_payment_id: `sandbox_${Date.now()}`,
        payment_group: "Sandbox_Test",
        payment_status: "SUCCESS",
      }
    }

    if (!successfulPayment) {
      return new Response(
        JSON.stringify({ status: "not_paid", message: "Payment not confirmed by Cashfree" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      )
    }

    // ── 6. Update payment_orders to paid ──────────────────────────────────
    const cashfreePaymentId = successfulPayment.cf_payment_id?.toString() || ""
    const paymentMethod     = successfulPayment.payment_group || "Online"

    await serviceClient
      .from("payment_orders")
      .update({
        status:              "paid",
        cashfree_payment_id: cashfreePaymentId,
        updated_at:          new Date().toISOString(),
      })
      .eq("id", order.id)

    // ── 7. Insert into payments table (single record for this transaction) ─
    const amountInINR  = order.amount
    const currentMonth = order.month_key || new Date().toISOString().substring(0, 7)

    // ── 8. Resolve enrollment ID ──────────────────────────────────────────
    let finalEnrollmentId = order.enrollment_id
    if (!finalEnrollmentId) {
      // Fallback: look up by student+teacher (handles older orders with null enrollment_id)
      const { data: envData } = await serviceClient
        .from("enrollments")
        .select("id")
        .eq("student_id", order.student_id)
        .eq("teacher_id", order.teacher_id)
        .single()
      if (envData) finalEnrollmentId = envData.id
    }

    if (!finalEnrollmentId) {
      throw new Error("Cannot record payment: student has no enrollment.")
    }

    // ── 7 & 9. Resolve enrollment, allocate, THEN insert payment with correct advance_amount ──
    // (Payment insert is deferred to after allocation so we know exact advance_amount)

    // ── 9. OLDEST-FIRST PAYMENT DISTRIBUTION ─────────────────────────────
    // Fetch ALL pending/partial months for this enrollment, oldest first
    const { data: pendingRecords } = await serviceClient
      .from("fee_records")
      .select("*")
      .eq("enrollment_id", finalEnrollmentId)
      .in("status", ["pending", "partial"])
      .order("month_key", { ascending: true })

    let remainingAmount = amountInINR
    const affectedMonths: string[] = []

    if (pendingRecords && pendingRecords.length > 0) {
      // Distribute payment across pending months oldest → newest
      for (const record of pendingRecords) {
        if (remainingAmount <= 0) break

        const currentPaid = record.paid_amount || 0
        const totalDue    = record.total_amount || 0
        const stillOwed   = totalDue - currentPaid

        if (stillOwed <= 0) continue

        const applyNow = Math.min(remainingAmount, stillOwed)
        const newPaid  = currentPaid + applyNow
        remainingAmount -= applyNow

        let newStatus = "partial"
        if (newPaid >= totalDue) newStatus = "paid"

        await serviceClient
          .from("fee_records")
          .update({ paid_amount: newPaid, status: newStatus })
          .eq("id", record.id)

        affectedMonths.push(`${record.month_key} → ${newStatus} (applied ₹${applyNow})`)
        console.log(`Applied ₹${applyNow} to ${record.month_key} → ${newStatus}`)
      }

      // After all pending months settled, remaining → advance balance
      if (remainingAmount > 0) {
        console.log(`All pending months cleared. Adding ₹${remainingAmount} to advance balance`)
        const { data: enrollment } = await serviceClient
          .from("enrollments")
          .select("advance_balance")
          .eq("id", finalEnrollmentId)
          .single()

        await serviceClient
          .from("enrollments")
          .update({ advance_balance: (enrollment?.advance_balance || 0) + remainingAmount })
          .eq("id", finalEnrollmentId)
      }

      // Now insert the payment record — advance_amount is ONLY the excess (remainder after all months paid)
      await serviceClient.from("payments").insert({
        enrollment_id:       finalEnrollmentId,
        student_id:          order.student_id,
        teacher_id:          order.teacher_id,
        amount:              amountInINR,
        advance_amount:      remainingAmount,  // ← exact amount that went to advance (0 if fully consumed by months)
        payment_mode:        `Online (Cashfree ${paymentMethod})`,
        transaction_id:      cashfreePaymentId,
        month_key:           currentMonth,
        cashfree_payment_id: cashfreePaymentId,
        settlement_status:   "pending",
        payment_provider:    "cashfree",
      })

    } else {
      // No pending months — apply to current month (or create if missing)
      const { data: currentRecord } = await serviceClient
        .from("fee_records")
        .select("*")
        .eq("enrollment_id", finalEnrollmentId)
        .eq("month_key", currentMonth)
        .single()

      if (currentRecord) {
        const currentPaid    = currentRecord.paid_amount || 0
        const totalDue       = currentRecord.total_amount || 0
        const newTotal       = currentPaid + amountInINR
        const regularPayment = Math.min(newTotal, totalDue)
        const excess         = Math.max(0, newTotal - totalDue)

        let newStatus = "pending"
        if (regularPayment >= totalDue) newStatus = "paid"
        else if (regularPayment > 0)    newStatus = "partial"

        await serviceClient
          .from("fee_records")
          .update({ paid_amount: regularPayment, status: newStatus })
          .eq("id", currentRecord.id)

        if (excess > 0) {
          const { data: enrollment } = await serviceClient
            .from("enrollments")
            .select("advance_balance")
            .eq("id", finalEnrollmentId)
            .single()

          await serviceClient
            .from("enrollments")
            .update({ advance_balance: (enrollment?.advance_balance || 0) + excess })
            .eq("id", finalEnrollmentId)
          console.log(`Added ₹${excess} to advance balance`)
        }
        affectedMonths.push(`${currentMonth} → ${newStatus}`)

        // Insert payment — advance_amount is only the excess
        await serviceClient.from("payments").insert({
          enrollment_id:       finalEnrollmentId,
          student_id:          order.student_id,
          teacher_id:          order.teacher_id,
          amount:              amountInINR,
          advance_amount:      excess,  // ← exact overpaid amount that went to advance
          payment_mode:        `Online (Cashfree ${paymentMethod})`,
          transaction_id:      cashfreePaymentId,
          month_key:           currentMonth,
          cashfree_payment_id: cashfreePaymentId,
          settlement_status:   "pending",
          payment_provider:    "cashfree",
        })

      } else {
        // No fee record at all — create one for current month
        const { data: classData } = await serviceClient
          .from("enrollments")
          .select("teacher_classes(fee_amount)")
          .eq("id", finalEnrollmentId)
          .single()

        const classFee       = (classData as any)?.teacher_classes?.fee_amount || amountInINR
        const excess         = Math.max(0, amountInINR - classFee)
        const regularPayment = Math.min(amountInINR, classFee)

        let newStatus = "pending"
        if (regularPayment >= classFee) newStatus = "paid"
        else if (regularPayment > 0)    newStatus = "partial"

        await serviceClient
          .from("fee_records")
          .insert({
            enrollment_id: finalEnrollmentId,
            month_key:     currentMonth,
            total_amount:  classFee,
            paid_amount:   regularPayment,
            status:        newStatus,
          })

        if (excess > 0) {
          const { data: enrollment } = await serviceClient
            .from("enrollments")
            .select("advance_balance")
            .eq("id", finalEnrollmentId)
            .single()

          await serviceClient
            .from("enrollments")
            .update({ advance_balance: (enrollment?.advance_balance || 0) + excess })
            .eq("id", finalEnrollmentId)
          console.log(`Created fee record for ${currentMonth}, added ₹${excess} to advance`)
        }
        affectedMonths.push(`${currentMonth} → ${newStatus} (created)`)

        // Insert payment — advance_amount is only the excess
        await serviceClient.from("payments").insert({
          enrollment_id:       finalEnrollmentId,
          student_id:          order.student_id,
          teacher_id:          order.teacher_id,
          amount:              amountInINR,
          advance_amount:      excess,  // ← exact overpaid amount
          payment_mode:        `Online (Cashfree ${paymentMethod})`,
          transaction_id:      cashfreePaymentId,
          month_key:           currentMonth,
          cashfree_payment_id: cashfreePaymentId,
          settlement_status:   "pending",
          payment_provider:    "cashfree",
        })
      }
    }

    console.log("✅ Payment verified and recorded:", cashfree_order_id)
    console.log("Months affected:", affectedMonths)

    return new Response(
      JSON.stringify({
        status:           "paid",
        payment_id:       cashfreePaymentId,
        amount:           amountInINR,
        months_affected:  affectedMonths,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )

  } catch (error: any) {
    console.error("verify-cashfree-payment error:", error)
    return new Response(
      JSON.stringify({ error: error?.message || "Verification failed" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }
})
