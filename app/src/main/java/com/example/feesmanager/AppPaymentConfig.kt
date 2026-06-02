package com.example.feesmanager

/**
 * AppPaymentConfig — Feature flag for payment provider.
 *
 * Change PAYMENT_PROVIDER to switch between Razorpay and Cashfree.
 * Both implementations remain in the codebase for safe rollback.
 *
 * Supabase URL is the same for both — read from SupabaseManager.
 */
object AppPaymentConfig {

    /**
     * Active payment provider.
     * Set to "CASHFREE" to use Cashfree Easy Split.
     * Set to "RAZORPAY" to fall back to existing Razorpay Route.
     */
    const val PAYMENT_PROVIDER = "CASHFREE"  // ← Change to "RAZORPAY" to rollback

    val isCashfree: Boolean get() = PAYMENT_PROVIDER == "CASHFREE"
    val isRazorpay: Boolean get() = PAYMENT_PROVIDER == "RAZORPAY"

    /**
     * Cashfree environment.
     * "TEST" = sandbox (for development)
     * "PROD" = production (for live payments)
     * ⚠️ Change to "PROD" when going live — also update in Supabase Edge Function env vars.
     */
    const val CASHFREE_ENV = "TEST"  // ← Change to "PROD" before going live

    // Supabase project URL — both providers use the same Supabase project
    const val SUPABASE_URL = "https://vtpguytfeqbpysxbppyv.supabase.co"

    // Edge function endpoint paths
    const val FN_CASHFREE_CREATE_VENDOR = "$SUPABASE_URL/functions/v1/create-cashfree-vendor"
    const val FN_CASHFREE_CREATE_ORDER  = "$SUPABASE_URL/functions/v1/create-cashfree-order"
    const val FN_RAZORPAY_CREATE_ORDER  = "$SUPABASE_URL/functions/v1/create-payment-order"
}
