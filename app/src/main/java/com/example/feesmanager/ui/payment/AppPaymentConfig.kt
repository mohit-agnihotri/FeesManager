package com.example.feesmanager.ui.payment

/**
 * AppPaymentConfig — Cashfree Payment Configuration.
 *
 * Supabase URL is read from this configuration to point to Edge Functions.
 */
object AppPaymentConfig {

    /**
     * Cashfree environment.
     * "TEST" = sandbox (for development)
     * "PROD" = production (for live payments)
     * ⚠️ Change to "PROD" when going live — also update in Supabase Edge Function env vars.
     */
    const val CASHFREE_ENV = "TEST"  // 👈 Change to "PROD" before going live

    // Supabase project URL
    const val SUPABASE_URL = "https://vtpguytfeqbpysxbppyv.supabase.co"

    // Edge function endpoint paths
    const val FN_CASHFREE_CREATE_VENDOR  = "$SUPABASE_URL/functions/v1/create-cashfree-vendor"
    const val FN_CASHFREE_CREATE_ORDER   = "$SUPABASE_URL/functions/v1/create-cashfree-order"
    const val FN_CASHFREE_SYNC_VENDOR    = "$SUPABASE_URL/functions/v1/sync-cashfree-vendor"
    const val FN_CASHFREE_VERIFY_PAYMENT = "$SUPABASE_URL/functions/v1/verify-cashfree-payment"
    const val FN_MONTHLY_ROLLOVER        = "$SUPABASE_URL/functions/v1/monthly-rollover"
}