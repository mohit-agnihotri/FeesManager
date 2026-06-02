# 🚀 FeesManager v8 — Cashfree Easy Split Setup Guide

Read this completely before opening Android Studio.
Everything you need to do manually is listed here step-by-step.

---

## WHAT WAS CHANGED (Summary)

| File | Change |
|---|---|
| `AppPaymentConfig.kt` | NEW — Feature flag: switch between Cashfree and Razorpay |
| `CashfreeOnboardingActivity.kt` | NEW — Teacher payment setup screen |
| `activity_cashfree_onboarding.xml` | NEW — Layout for above |
| `data/model/CashfreeVendorStatus.kt` | NEW — Vendor status model |
| `data/repository/CashfreeRepository.kt` | NEW — All Cashfree API calls |
| `ui/payment/CashfreeSetupViewModel.kt` | NEW — ViewModel for onboarding |
| `PayFeesActivity.kt` | MODIFIED — supports both Razorpay + Cashfree via flag |
| `ui/fees/PayFeesViewModel.kt` | MODIFIED — added Cashfree order creation |
| `data/repository/FeesRepository.kt` | MODIFIED — Cashfree vendor status check |
| `DashboardActivity.kt` | MODIFIED — routes to Cashfree or Razorpay setup |
| `activity_pay_fees.xml` | MODIFIED — added id to provider note TextView |
| `app/build.gradle.kts` | MODIFIED — added Cashfree SDK dependency |
| `settings.gradle.kts` | MODIFIED — added Cashfree Maven repo |
| `AndroidManifest.xml` | MODIFIED — added CashfreeOnboardingActivity |
| `supabase/functions/create-cashfree-vendor/` | NEW Edge Function |
| `supabase/functions/create-cashfree-order/` | NEW Edge Function |
| `supabase/functions/cashfree-payment-webhook/` | NEW Edge Function |
| `supabase/functions/cashfree-settlement-webhook/` | NEW Edge Function |
| `cashfree_migration_v4.sql` | NEW — Additive DB migration (no drops) |

**NOT CHANGED:** RazorpayOnboardingActivity, Razorpay edge functions, all Razorpay DB columns, all other screens, AI, chat, attendance, analytics, backup — everything else untouched.

---

## STEP 1 — Run the Database Migration

1. Go to https://supabase.com/dashboard
2. Open your project → SQL Editor
3. Click **New Query**
4. Copy and paste the entire contents of `cashfree_migration_v4.sql`
5. Click **Run**
6. You should see: `Cashfree Migration v4 complete ✅`

---

## STEP 2 — Create a Cashfree Account

1. Go to https://merchant.cashfree.com/
2. Sign up with your business details
3. Complete the merchant KYC (PAN, bank account)
4. Once approved, go to **API Keys** section
5. Note down your:
   - **App ID** (looks like: `CF123456TEST` for sandbox)
   - **Secret Key** (long string)

For testing, use **Sandbox credentials** from:
https://merchant.cashfree.com/merchants/developers

---

## STEP 3 — Enable Easy Split on Your Cashfree Account

1. Login to Cashfree Merchant Dashboard
2. Go to **Easy Split** → **Enable**
3. Accept Easy Split terms
4. This may require business verification (1–2 days)

---

## STEP 4 — Deploy the 4 New Supabase Edge Functions

Open terminal and run these commands one by one:

```bash
# Install Supabase CLI if not already installed
npm install -g supabase

# Login to Supabase
supabase login

# Link to your project (get project ref from Supabase dashboard URL)
supabase link --project-ref vtpguytfeqbpysxbppyv

# Deploy all 4 new functions
supabase functions deploy create-cashfree-vendor
supabase functions deploy create-cashfree-order
supabase functions deploy cashfree-payment-webhook
supabase functions deploy cashfree-settlement-webhook
```

> ⚠️ The existing Razorpay functions (create-payment-order, create-linked-account, razorpay-webhook) are NOT touched. Do NOT redeploy them.

---

## STEP 5 — Set Cashfree Secret Keys in Supabase

**NEVER put API keys in the Android app. They go here:**

In terminal:
```bash
supabase secrets set CASHFREE_APP_ID="your_app_id_here"
supabase secrets set CASHFREE_SECRET_KEY="your_secret_key_here"
supabase secrets set CASHFREE_ENV="TEST"
supabase secrets set PLATFORM_COMMISSION_PERCENT="0"
```

Or via Supabase Dashboard:
1. Go to your project → **Edge Functions** → **Manage Secrets**
2. Add:
   - `CASHFREE_APP_ID` = your App ID from Step 2
   - `CASHFREE_SECRET_KEY` = your Secret Key from Step 2
   - `CASHFREE_ENV` = `TEST` (change to `PROD` when going live)
   - `PLATFORM_COMMISSION_PERCENT` = `0`

---

## STEP 6 — Configure Cashfree Payment Webhook

1. Login to Cashfree Merchant Dashboard
2. Go to **Developers** → **Webhooks**
3. Add new webhook URL:
   ```
   https://vtpguytfeqbpysxbppyv.supabase.co/functions/v1/cashfree-payment-webhook
   ```
4. Select events: ✅ `PAYMENT_SUCCESS_WEBHOOK`
5. Add settlement webhook URL:
   ```
   https://vtpguytfeqbpysxbppyv.supabase.co/functions/v1/cashfree-settlement-webhook
   ```
6. Select events: ✅ `VENDOR_SETTLEMENT_SUCCESS`, ✅ `VENDOR_SETTLEMENT_INITIATED`, ✅ `VENDOR_SETTLEMENT_REVERSED`

---

## STEP 7 — Open in Android Studio

1. Open Android Studio
2. **File → Open** → Select the `FeesManager_v8` folder
3. Wait for Gradle sync to complete
4. If Gradle sync fails on Cashfree dependency, verify Step 8 below

---

## STEP 8 — Verify Cashfree SDK Resolves

If Android Studio can't find `com.cashfree.pg:cf-android-sdk:2.3.7`:

1. Open `settings.gradle.kts`
2. Confirm this line is there:
   ```kotlin
   maven { url = uri("https://maven.cashfree.com/release") }
   ```
3. Click **Sync Now** in Android Studio
4. If still failing, try:
   ```kotlin
   // Alternative: use JCenter mirror
   maven { url = uri("https://dl.bintray.com/cashfree/maven") }
   ```

---

## STEP 9 — Test in Sandbox Mode

`AppPaymentConfig.CASHFREE_ENV` is already set to `"TEST"` in the code.

**Test Teacher Flow:**
1. Login as teacher
2. Tap the 💳 payment button on dashboard
3. Enter fake details:
   - Account Holder: `Test Teacher`
   - Account Number: `026291800001191`
   - IFSC: `YESB0000262`
   - PAN: `ABCDE1234F`
   - Phone: `9999999999`
4. Tap Submit
5. Vendor will be created in Cashfree sandbox

**Test Student Payment:**
1. Login as student
2. Go to Pay Fees
3. Enter amount → Pay Online
4. Cashfree test checkout opens
5. Use test card: `4111 1111 1111 1111`, CVV: `123`, Expiry: any future date
6. OTP: `123456`

**Cashfree Sandbox Test Cards:**
- Success: `4111 1111 1111 1111`
- Failure: `4111 1111 1111 1100`

---

## STEP 10 — Going Live (Production)

When ready to go live:

1. Change `CASHFREE_ENV` secret in Supabase from `TEST` → `PROD`
2. Change in `AppPaymentConfig.kt`:
   ```kotlin
   const val CASHFREE_ENV = "PROD"
   ```
3. Replace Cashfree sandbox API keys with PRODUCTION keys in Supabase secrets
4. Update webhook URLs in Cashfree dashboard if different
5. Make sure Cashfree Easy Split is fully enabled on your live account

---

## ROLLBACK TO RAZORPAY

If anything goes wrong, open `AppPaymentConfig.kt` and change ONE line:

```kotlin
const val PAYMENT_PROVIDER = "RAZORPAY"  // was "CASHFREE"
```

Rebuild the app. Everything routes back to Razorpay instantly.
No database changes needed. No code deletions needed.

---

## TROUBLESHOOTING

| Problem | Solution |
|---|---|
| `Vendor creation failed: 409 Conflict` | Vendor ID already exists — teacher already registered. Reload the screen. |
| `Payment setup incomplete. Vendor status: IN_BENE_CREATION` | Bank verification in progress. Wait 30 min - 24 hours. |
| `No payment_session_id returned` | Check CASHFREE_APP_ID and CASHFREE_SECRET_KEY are set correctly in Supabase secrets |
| `Invalid signature` in webhook logs | Check CASHFREE_SECRET_KEY matches what's in Cashfree dashboard |
| Cashfree SDK not found in Android Studio | Add maven repo in settings.gradle.kts (Step 8) |
| `CFException: Environment error` | CASHFREE_ENV must match your API key type (TEST keys → TEST env) |
| Teacher sees "Online Payment Not Available" | Vendor status must be ACTIVE (not IN_BENE_CREATION). Wait for bank verification. |

---

## DATABASE COLUMNS ADDED (Reference)

**teachers table:**
- `cashfree_vendor_id` TEXT
- `vendor_status` TEXT (default: 'not_started')
- `kyc_status` TEXT (default: 'not_started')
- `pan_number` TEXT
- `teacher_phone` TEXT

**payment_orders table:**
- `cashfree_order_id` TEXT
- `cashfree_payment_id` TEXT
- `payment_session_id` TEXT
- `settlement_status` TEXT
- `settlement_utr` TEXT
- `payment_provider` TEXT (default: 'razorpay')
- `razorpay_order_id` — made NULLABLE (was NOT NULL)

**payments table:**
- `cashfree_payment_id` TEXT
- `settlement_status` TEXT
- `settlement_utr` TEXT
- `payment_provider` TEXT

**webhook_logs table:**
- `cashfree_order_id` TEXT
- `cashfree_payment_id` TEXT
- `payment_provider` TEXT
