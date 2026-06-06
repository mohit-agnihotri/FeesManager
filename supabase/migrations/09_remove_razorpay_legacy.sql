-- ============================================================================
-- FEES MANAGER MIGRATION - Remove legacy Razorpay columns
-- Run this in Supabase SQL Editor
-- ============================================================================

-- 1. Drop from teachers table
ALTER TABLE teachers DROP COLUMN IF EXISTS razorpay_account_id;
ALTER TABLE teachers DROP COLUMN IF EXISTS razorpay_onboarding_status;

-- 2. Drop from payment_orders table
ALTER TABLE payment_orders DROP COLUMN IF EXISTS razorpay_order_id;
ALTER TABLE payment_orders DROP COLUMN IF EXISTS razorpay_payment_id;
ALTER TABLE payment_orders DROP COLUMN IF EXISTS razorpay_signature;
ALTER TABLE payment_orders DROP COLUMN IF EXISTS razorpay_transfer_id;

-- 3. Drop from webhook_logs table
ALTER TABLE webhook_logs DROP COLUMN IF EXISTS razorpay_payment_id;
ALTER TABLE webhook_logs DROP COLUMN IF EXISTS razorpay_order_id;

-- Done
SELECT 'Legacy Razorpay columns removed completely ✅' AS status;
