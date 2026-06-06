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
    // 1. Auth check
    const authorization = req.headers.get("Authorization")
    if (!authorization) throw new Error("Missing Authorization header")

    const token = authorization.replace("Bearer ", "")

    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_ANON_KEY") ?? ""
    )

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token)
    if (authError || !user) throw new Error("Unauthorized")

    // 2. Fetch current vendor ID from DB
    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL")              ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    const { data: teacher, error: dbError } = await serviceClient
      .from("teachers")
      .select("cashfree_vendor_id, vendor_status, kyc_status, bank_account_name, bank_account_number, bank_ifsc")
      .eq("id", user.id)
      .single()

    if (dbError || !teacher) throw new Error("Teacher not found")

    const vendorId = teacher.cashfree_vendor_id
    if (!vendorId) {
      return new Response(
        JSON.stringify({
          vendor_status: "not_started",
          kyc_status: "not_started",
          bank_account_name: "",
          bank_account_number: "",
          bank_ifsc: ""
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      )
    }

    // 3. Fetch latest status from Cashfree
    console.log("Fetching Cashfree vendor status for:", vendorId)
    const cfResponse = await fetch(`${CASHFREE_BASE_URL}/pg/easy-split/vendors/${vendorId}`, {
      method:  "GET",
      headers: {
        "x-client-id":     CASHFREE_APP_ID,
        "x-client-secret": CASHFREE_SECRET_KEY,
        "x-api-version":   "2023-08-01",
      },
    })

    const cfData = await cfResponse.json()
    console.log("Cashfree vendor status response:", JSON.stringify(cfData))

    if (!cfResponse.ok) {
      const errMsg = cfData?.message || cfData?.error || JSON.stringify(cfData)
      throw new Error(`Cashfree Error: ${errMsg}`)
    }

    // Determine latest status
    let newVendorStatus = cfData.status || teacher.vendor_status
    let newKycStatus = "IN_REVIEW"
    
    // Sometimes Cashfree doesn't send kyc_details if not requested in a specific way,
    // but if vendor is ACTIVE, KYC is verified.
    if (cfData.kyc_details && cfData.kyc_details.status) {
      newKycStatus = cfData.kyc_details.status
    } else if (newVendorStatus === "ACTIVE") {
      newKycStatus = "VERIFIED"
    }

    // ── Sandbox Override to unblock developer testing ──────────────────────
    if (CASHFREE_ENV === "TEST") {
      newVendorStatus = "ACTIVE"
      newKycStatus    = "VERIFIED"
    }

    // 4. Update Supabase if changed
    if (newVendorStatus !== teacher.vendor_status || newKycStatus !== teacher.kyc_status) {
      await serviceClient
        .from("teachers")
        .update({
          vendor_status: newVendorStatus,
          kyc_status: newKycStatus,
        })
        .eq("id", user.id)
    }

    return new Response(
      JSON.stringify({
        cashfree_vendor_id: vendorId,
        vendor_status: newVendorStatus,
        kyc_status: newKycStatus,
        bank_account_name: teacher.bank_account_name,
        bank_account_number: teacher.bank_account_number,
        bank_ifsc: teacher.bank_ifsc
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )

  } catch (error: any) {
    console.error("sync-cashfree-vendor error:", error)
    return new Response(
      JSON.stringify({ error: error?.message || "Unknown error" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }
})
