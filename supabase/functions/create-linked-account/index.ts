import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.3"

const RAZORPAY_KEY_ID = Deno.env.get("RAZORPAY_KEY_ID") || ""
const RAZORPAY_KEY_SECRET = Deno.env.get("RAZORPAY_KEY_SECRET") || ""

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders })
  }

  try {
    // =========================
    // AUTH CHECK
    // =========================
    const authorization = req.headers.get("Authorization")

    if (!authorization) {
      throw new Error("Missing Authorization header")
    }

    const token = authorization.replace("Bearer ", "")

    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_ANON_KEY") ?? ""
    )

    const {
      data: { user },
      error: authError,
    } = await supabaseClient.auth.getUser(token)

    console.log("USER:", user?.id)
    console.log("AUTH ERROR:", authError)

    if (authError || !user) {
      throw new Error("Unauthorized")
    }

    // =========================
    // REQUEST DATA
    // =========================
    const {
      account_name,
      account_number,
      ifsc,
    } = await req.json()

    if (!account_name || !account_number || !ifsc) {
      throw new Error("Missing bank details")
    }

    // =========================
    // RAZORPAY REQUEST
    // =========================
    const razorpayAuth =
      "Basic " +
      btoa(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`)

    const razorpayResponse = await fetch(
      "https://api.razorpay.com/v1/beta/accounts",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: razorpayAuth,
        },
        body: JSON.stringify({
          email: user.email,
          phone: "9999999999",
          type: "route",
          reference_id: user.id,
          legal_business_name: account_name,
          business_type: "individual",
          contact_name: account_name,

          profile: {
            category: "education",
            subcategory: "schools",
            addresses: {
              registered: {
                street1: "Online",
                city: "Bhopal",
                state: "Madhya Pradesh",
                postal_code: "462001",
                country: "IN",
              },
            },
          },

          legal_info: {
            pan: "ABCDE1234F",
          },

          bank_account: {
            beneficiary_name: account_name,
            account_number: account_number,
            ifsc_code: ifsc,
          },
        }),
      }
    )

    const razorpayData = await razorpayResponse.json()

    console.log(
      "RAZORPAY RESPONSE:",
      JSON.stringify(razorpayData)
    )

    if (!razorpayResponse.ok) {
      throw new Error(
        razorpayData?.error?.description ||
          JSON.stringify(razorpayData)
      )
    }

    const accountId = razorpayData.id

    // =========================
    // UPDATE TEACHERS TABLE
    // =========================
    const serviceClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    )

    const { error: updateError } = await serviceClient
      .from("teachers")
      .update({
        razorpay_account_id: accountId,
        razorpay_onboarding_status: "activated",
        bank_account_name: account_name,
        bank_account_number: account_number,
        bank_ifsc: ifsc,
      })
      .eq("id", user.id)

    if (updateError) {
      console.error(updateError)
      throw updateError
    }

    return new Response(
      JSON.stringify({
        success: true,
        account_id: accountId,
      }),
      {
        status: 200,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
        },
      }
    )
  } catch (error: any) {
    console.error("FUNCTION ERROR:", error)

    return new Response(
      JSON.stringify({
        error: error?.message || "Unknown error",
      }),
      {
        status: 400,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
        },
      }
    )
  }
})