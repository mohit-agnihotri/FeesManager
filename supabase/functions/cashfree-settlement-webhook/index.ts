// supabase/functions/cashfree-settlement-webhook/index.ts
// Handles Cashfree VENDOR_SETTLEMENT_* webhook events.
// Updates settlement_status and settlement_utr in payment_orders and payments.
// Also updates vendor_status/kyc_status in teachers table.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.3"

const CASHFREE_SECRET_KEY = Deno.env.get("CASHFREE_SECRET_KEY") || ""

serve(async (req) => {
  try {
    const rawBody   = await req.text()
    const timestamp = req.headers.get("x-webhook-timestamp") || ""
    const signature = req.headers.get("x-webhook-signature") || ""

    if (!timestamp || !signature) {
      return new Response("Missing headers", { status: 400 })
    }

    // ── Verify Cashfree signature ─────────────────────────────────────────
    const encoder   = new TextEncoder()
    const keyData   = encoder.encode(CASHFREE_SECRET_KEY)
    const msgData   = encoder.encode(timestamp + rawBody)

    const cryptoKey = await crypto.subtle.importKey(
      "raw", keyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
    )
    const sigBuffer   = await crypto.subtle.sign("HMAC", cryptoKey, msgData)
    const sigBytes    = new Uint8Array(sigBuffer)
    const expectedSig = btoa(String.fromCharCode(...sigBytes))

    if (expectedSig !== signature) {
      return new Response("Invalid signature", { status: 401 })
    }

    const payload   = JSON.parse(rawBody)
    const eventType = payload.type || ""
    const settlement = payload.data?.settlement

    console.log("Settlement webhook:", eventType, JSON.stringify(settlement))

    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL")              ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    // Log it
    await serviceClient.from("webhook_logs").insert({
      event_type:       eventType,
      payload:          payload,
      payment_provider: "cashfree",
    })

    // Map Cashfree settlement events to our statuses
    const settlementStatusMap: Record<string, string> = {
      "VENDOR_SETTLEMENT_CREATED":   "VENDOR_SETTLEMENT_INITIATED",
      "VENDOR_SETTLEMENT_INITIATED": "VENDOR_SETTLEMENT_INITIATED",
      "VENDOR_SETTLEMENT_SUCCESS":   "VENDOR_SETTLEMENT_SUCCESS",
      "VENDOR_SETTLEMENT_REVERSED":  "VENDOR_SETTLEMENT_REVERSED",
    }

    const newSettlementStatus = settlementStatusMap[eventType]
    if (!newSettlementStatus || !settlement) {
      return new Response(JSON.stringify({ status: "ok", note: "Unhandled event" }), { status: 200 })
    }

    const vendorId = settlement.vendor_id
    const utr      = settlement.utr || null

    // Update all payments for this vendor that are still pending settlement
    if (vendorId) {
      // Find teacher by vendor ID
      const { data: teacher } = await serviceClient
        .from("teachers")
        .select("id")
        .eq("cashfree_vendor_id", vendorId)
        .single()

      if (teacher) {
        // Update settlement status on all cashfree payments for this teacher
        await serviceClient
          .from("payments")
          .update({
            settlement_status: newSettlementStatus,
            settlement_utr:    utr,
          })
          .eq("teacher_id",    teacher.id)
          .eq("payment_provider", "cashfree")
          .eq("settlement_status", "pending")

        // Also update payment_orders
        await serviceClient
          .from("payment_orders")
          .update({
            settlement_status: newSettlementStatus,
            settlement_utr:    utr,
          })
          .eq("teacher_id",    teacher.id)
          .eq("payment_provider", "cashfree")
          .eq("settlement_status", "pending")

        // If success, update vendor_status in teachers
        if (eventType === "VENDOR_SETTLEMENT_SUCCESS") {
          await serviceClient
            .from("teachers")
            .update({ vendor_status: "ACTIVE" })
            .eq("id", teacher.id)
        }
      }
    }

    return new Response(JSON.stringify({ status: "ok" }), { status: 200 })

  } catch (error: any) {
    console.error("cashfree-settlement-webhook error:", error)
    return new Response(error.message, { status: 500 })
  }
})
