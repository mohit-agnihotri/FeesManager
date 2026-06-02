-- ============================================================================
-- FEES MANAGER v3 MIGRATION — Razorpay Route & Secure Payments
-- Run this in Supabase SQL Editor
-- ============================================================================

-- ── 1. Update teachers table ─────────────────────────────────────────────

ALTER TABLE teachers ADD COLUMN IF NOT EXISTS razorpay_account_id TEXT;
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS razorpay_onboarding_status TEXT DEFAULT 'not_started';
-- Values: 'not_started', 'pending', 'activated', 'suspended'

ALTER TABLE teachers ADD COLUMN IF NOT EXISTS bank_account_name TEXT;
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS bank_account_number TEXT;
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS bank_ifsc TEXT;

-- ── 2. Create payment_orders table ───────────────────────────────────────

CREATE TABLE IF NOT EXISTS payment_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  razorpay_order_id TEXT UNIQUE NOT NULL,
  enrollment_id UUID REFERENCES enrollments(id) ON DELETE CASCADE,
  student_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
  teacher_id UUID REFERENCES teachers(id) ON DELETE CASCADE,
  amount INTEGER NOT NULL,          -- in paise (₹100 = 10000 paise)
  currency TEXT DEFAULT 'INR',
  status TEXT DEFAULT 'created',    -- 'created', 'paid', 'failed', 'refunded'
  razorpay_payment_id TEXT,
  razorpay_signature TEXT,
  razorpay_transfer_id TEXT,
  month_key TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ── 3. Create webhook_logs table ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS webhook_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type TEXT NOT NULL,
  razorpay_payment_id TEXT,
  razorpay_order_id TEXT,
  payload JSONB,
  processed BOOLEAN DEFAULT FALSE,
  error_message TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ── 4. RLS policies for payment_orders ───────────────────────────────────

ALTER TABLE payment_orders ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Teachers can read own orders" ON payment_orders;
CREATE POLICY "Teachers can read own orders"
ON payment_orders FOR SELECT
USING (auth.uid() = teacher_id);

DROP POLICY IF EXISTS "Students can read own orders" ON payment_orders;
CREATE POLICY "Students can read own orders"
ON payment_orders FOR SELECT
USING (auth.uid() = student_id);

-- Note: No INSERT/UPDATE policies for anon/authenticated roles.
-- All writes must go through Edge Functions (which bypass RLS via Service Role).

-- ── 5. Enable realtime for payment_orders ────────────────────────────────

DO $$
BEGIN
  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE payment_orders;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
END $$;

-- ── 6. Create indexes ────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_payment_orders_razorpay_order
ON payment_orders(razorpay_order_id);

CREATE INDEX IF NOT EXISTS idx_payment_orders_razorpay_payment
ON payment_orders(razorpay_payment_id);

-- ── Done ──────────────────────────────────────────────────────────────────
SELECT 'Migration v3 complete ✅' AS status;
