// supabase/functions/monthly-rollover/index.ts
//
// Called when the student/teacher opens the dashboard.
// Creates fee_records for the CURRENT month for all of a teacher's approved students.
// Automatically applies any existing advance_balance to reduce pending dues.
//
// This makes rollover SERVER-SIDE and RELIABLE — no more depending on Android client timing.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.3"

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
    const body = await req.json()
    const teacher_id = body.teacher_id
    if (!teacher_id) throw new Error("Missing teacher_id")

    // ── 3. Service client (bypasses RLS) ──────────────────────────────────
    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL")              ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    const currentMonth = new Date().toISOString().substring(0, 7)
    console.log(`Monthly rollover for teacher ${teacher_id}, month: ${currentMonth}`)

    // ── 4. Get all approved enrollments for this teacher ──────────────────
    const { data: enrollments, error: enrollError } = await serviceClient
      .from("enrollments")
      .select("id, advance_balance, teacher_classes(fee_amount)")
      .eq("teacher_id", teacher_id)
      .eq("status", "approved")
      .is("deleted_at", null)

    if (enrollError) throw enrollError

    let created = 0
    const results: any[] = []

    for (const enrollment of (enrollments || [])) {
      // Check if fee record already exists for this month
      const { data: existing } = await serviceClient
        .from("fee_records")
        .select("id")
        .eq("enrollment_id", enrollment.id)
        .eq("month_key", currentMonth)
        .single()

      if (existing) {
        results.push({ enrollment_id: enrollment.id, skipped: true })
        continue
      }

      const classFee   = (enrollment as any).teacher_classes?.fee_amount || 0
      const advBalance = enrollment.advance_balance || 0

      // Apply advance balance: use as much as needed to cover the fee
      const appliedAdv = Math.min(advBalance, classFee)
      const paidAmt    = appliedAdv
      const newAdvBal  = advBalance - appliedAdv

      let newStatus = "pending"
      if (appliedAdv >= classFee && classFee > 0) newStatus = "paid"
      else if (appliedAdv > 0)                    newStatus = "partial"
      else if (classFee === 0)                     newStatus = "paid"

      // Create fee record for this month
      await serviceClient
        .from("fee_records")
        .insert({
          enrollment_id: enrollment.id,
          month_key:     currentMonth,
          total_amount:  classFee,
          paid_amount:   paidAmt,
          status:        newStatus,
        })

      // Deduct used advance from enrollment
      if (appliedAdv > 0) {
        await serviceClient
          .from("enrollments")
          .update({ advance_balance: newAdvBal })
          .eq("id", enrollment.id)

        console.log(`Enrollment ${enrollment.id}: applied ₹${appliedAdv} advance → ${newStatus}, remaining advance = ₹${newAdvBal}`)
      }

      created++
      results.push({
        enrollment_id: enrollment.id,
        month:         currentMonth,
        class_fee:     classFee,
        advance_used:  appliedAdv,
        paid:          paidAmt,
        status:        newStatus,
      })
    }

    console.log(`✅ Rollover complete: created ${created} fee records for ${currentMonth}`)

    return new Response(
      JSON.stringify({
        success:  true,
        month:    currentMonth,
        created,
        results,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )

  } catch (error: any) {
    console.error("monthly-rollover error:", error)
    return new Response(
      JSON.stringify({ error: error?.message || "Rollover failed" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }
})
