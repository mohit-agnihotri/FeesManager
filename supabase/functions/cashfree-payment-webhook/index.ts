// supabase/functions/cashfree-payment-webhook/index.ts
// Handles Cashfree PAYMENT_SUCCESS_WEBHOOK events.
// Signature verification: HMAC-SHA256 → Base64 over (timestamp + rawBody)
// Updates payment_orders, inserts into payments, updates fee_records.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.3"

const CASHFREE_SECRET_KEY = Deno.env.get("CASHFREE_SECRET_KEY") || ""

serve(async (req) => {
  try {
    // ── 1. Read raw body and headers ─────────────────────────────────────
    const rawBody   = await req.text()
    const timestamp = req.headers.get("x-webhook-timestamp") || ""
    const signature = req.headers.get("x-webhook-signature") || ""

    if (!timestamp || !signature) {
      console.error("Missing webhook headers")
      return new Response("Missing headers", { status: 400 })
    }

    // ── 2. Verify Cashfree signature ─────────────────────────────────────
    // Algorithm: Base64(HMAC-SHA256(timestamp + rawBody, secretKey))
    const encoder     = new TextEncoder()
    const keyData     = encoder.encode(CASHFREE_SECRET_KEY)
    const msgData     = encoder.encode(timestamp + rawBody)

    const cryptoKey = await crypto.subtle.importKey(
      "raw", keyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
    )
    const sigBuffer   = await crypto.subtle.sign("HMAC", cryptoKey, msgData)
    const sigBytes    = new Uint8Array(sigBuffer)
    const expectedSig = btoa(String.fromCharCode(...sigBytes))

    if (expectedSig !== signature) {
      console.error("Signature mismatch. Expected:", expectedSig, "Got:", signature)
      return new Response("Invalid signature", { status: 401 })
    }

    // ── 3. Parse payload ─────────────────────────────────────────────────
    const payload = JSON.parse(rawBody)
    const eventType = payload.type || payload.event

    console.log("Cashfree webhook event:", eventType)

    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL")              ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    // Log webhook
    await serviceClient.from("webhook_logs").insert({
      event_type:          eventType,
      cashfree_order_id:   payload.data?.order?.order_id    || null,
      cashfree_payment_id: payload.data?.payment?.cf_payment_id?.toString() || null,
      payload:             payload,
      payment_provider:    "cashfree",
    })

    // ── 4. Handle PAYMENT_SUCCESS_WEBHOOK ────────────────────────────────
    if (eventType === "PAYMENT_SUCCESS_WEBHOOK" || eventType === "PAYMENT_SUCCESS") {
      const orderData   = payload.data?.order
      const paymentData = payload.data?.payment

      const cashfreeOrderId   = orderData?.order_id
      const cashfreePaymentId = paymentData?.cf_payment_id?.toString() || ""
      const paymentMethod     = paymentData?.payment_group || paymentData?.payment_method || "Online"

      if (!cashfreeOrderId) {
        console.error("No order_id in webhook payload")
        return new Response("Missing order_id", { status: 200 }) // 200 to prevent retries
      }

      // Find order in our DB
      const { data: order } = await serviceClient
        .from("payment_orders")
        .select("*")
        .eq("cashfree_order_id", cashfreeOrderId)
        .single()

      if (!order || order.status === "paid") {
        console.log("Order not found or already paid:", cashfreeOrderId)
        return new Response(JSON.stringify({ status: "ok" }), { status: 200 })
      }

      const amountInINR   = order.amount  // already stored in INR
      const currentMonth  = order.month_key || new Date().toISOString().substring(0, 7)

      // Update payment_orders to paid
      await serviceClient
        .from("payment_orders")
        .update({
          status:              "paid",
          cashfree_payment_id: cashfreePaymentId,
          updated_at:          new Date().toISOString(),
        })
        .eq("id", order.id)

      // Insert into payments table (existing table — reused)
      await serviceClient.from("payments").insert({
        enrollment_id:       order.enrollment_id,
        student_id:          order.student_id,
        teacher_id:          order.teacher_id,
        amount:              amountInINR,
        payment_mode:        `Online (Cashfree ${paymentMethod})`,
        transaction_id:      cashfreePaymentId,
        month_key:           currentMonth,
        cashfree_payment_id: cashfreePaymentId,
        settlement_status:   "pending",
        payment_provider:    "cashfree",
      })

      // Update fee_records (same logic as Razorpay webhook — reused)
      const { data: feeRecord } = await serviceClient
        .from("fee_records")
        .select("*")
        .eq("enrollment_id", order.enrollment_id)
        .eq("month_key", currentMonth)
        .single()

      if (feeRecord) {
        const currentPaid    = feeRecord.paid_amount
        const totalDue       = feeRecord.total_amount
        const newTotal       = currentPaid + amountInINR
        const regularPayment = Math.min(newTotal, totalDue)
        const excess         = Math.max(0, newTotal - totalDue)

        let newStatus = "pending"
        if (regularPayment >= totalDue) newStatus = "paid"
        else if (regularPayment > 0)    newStatus = "partial"

        await serviceClient
          .from("fee_records")
          .update({ paid_amount: regularPayment, status: newStatus })
          .eq("id", feeRecord.id)

        if (excess > 0) {
          const { data: enrollment } = await serviceClient
            .from("enrollments")
            .select("advance_balance")
            .eq("id", order.enrollment_id)
            .single()

          if (enrollment) {
            await serviceClient
              .from("enrollments")
              .update({ advance_balance: (enrollment.advance_balance || 0) + excess })
              .eq("id", order.enrollment_id)
          }
        }
      }

      console.log("Payment processed successfully:", cashfreeOrderId)
    }

    return new Response(JSON.stringify({ status: "ok" }), { status: 200 })

  } catch (error: any) {
    console.error("cashfree-payment-webhook error:", error)
    return new Response(error.message, { status: 500 })
  }
})
