-- ============================================================================
-- FEES MANAGER v4 MIGRATION — Cashfree Easy Split (ADDITIVE ONLY)
-- Run this in Supabase SQL Editor
-- ⚠️  SAFE: Does NOT drop any existing Razorpay columns or tables
-- ⚠️  SAFE: All existing data and functionality is preserved
-- ============================================================================

-- ── 1. teachers table — Add Cashfree vendor columns ──────────────────────────
-- Razorpay columns (razorpay_account_id, razorpay_onboarding_status,
-- bank_account_name, bank_account_number, bank_ifsc) are KEPT intact.

ALTER TABLE teachers ADD COLUMN IF NOT EXISTS cashfree_vendor_id          TEXT;
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS vendor_status               TEXT DEFAULT 'not_started';
-- Values: 'not_started' | 'IN_BENE_CREATION' | 'ACTIVE' | 'BLOCKED'
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS kyc_status                  TEXT DEFAULT 'not_started';
-- Values: 'not_started' | 'IN_REVIEW' | 'VERIFIED' | 'REJECTED'
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS pan_number                  TEXT;
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS teacher_phone               TEXT;
-- bank_account_name, bank_account_number, bank_ifsc already exist from v3 ✅

-- ── 2. payment_orders table — Add Cashfree columns ───────────────────────────
-- Razorpay columns (razorpay_order_id, razorpay_payment_id, etc.) are KEPT intact.
-- The NOT NULL constraint on razorpay_order_id needs to be relaxed for new orders.

-- First make razorpay_order_id nullable so Cashfree orders don't need it
ALTER TABLE payment_orders ALTER COLUMN razorpay_order_id DROP NOT NULL;

ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS cashfree_order_id     TEXT;
ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS cashfree_payment_id   TEXT;
ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS payment_session_id    TEXT;
ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS settlement_status     TEXT DEFAULT 'pending';
-- Values: 'pending' | 'VENDOR_SETTLEMENT_INITIATED' | 'VENDOR_SETTLEMENT_SUCCESS' | 'VENDOR_SETTLEMENT_REVERSED' | 'FAILED'
ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS settlement_utr        TEXT;
ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS payment_provider      TEXT DEFAULT 'razorpay';
-- Values: 'razorpay' | 'cashfree'

-- ── 3. payments table — Add settlement tracking ───────────────────────────────
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cashfree_payment_id         TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS settlement_status            TEXT DEFAULT 'pending';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS settlement_utr               TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_provider             TEXT DEFAULT 'razorpay';

-- ── 4. webhook_logs table — Add Cashfree column ───────────────────────────────
-- Razorpay columns (razorpay_payment_id, razorpay_order_id) are KEPT intact.
ALTER TABLE webhook_logs ADD COLUMN IF NOT EXISTS cashfree_order_id       TEXT;
ALTER TABLE webhook_logs ADD COLUMN IF NOT EXISTS cashfree_payment_id     TEXT;
ALTER TABLE webhook_logs ADD COLUMN IF NOT EXISTS payment_provider        TEXT DEFAULT 'razorpay';

-- ── 5. Indexes for Cashfree lookups ──────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_payment_orders_cashfree_order
ON payment_orders(cashfree_order_id);

CREATE INDEX IF NOT EXISTS idx_payment_orders_cashfree_payment
ON payment_orders(cashfree_payment_id);

CREATE INDEX IF NOT EXISTS idx_teachers_cashfree_vendor
ON teachers(cashfree_vendor_id);

-- ── 6. RLS: payment_orders already has policies from v3 — no changes needed ──

-- ── Done ─────────────────────────────────────────────────────────────────────
SELECT 'Cashfree Migration v4 complete ✅' AS status;
