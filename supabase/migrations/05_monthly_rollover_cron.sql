-- ============================================================================
-- FEES MANAGER — Migration 05: Monthly Rollover Cron Job
-- Run this in Supabase SQL Editor
--
-- Sets up a pg_cron job that automatically creates fee_records
-- for every student on the 1st of each month at 6:00 AM IST (00:30 UTC)
-- Also applies advance_balance automatically.
-- ============================================================================

-- ── 1. Create the rollover SQL function (runs directly in DB, no Edge Function needed) ──

CREATE OR REPLACE FUNCTION do_monthly_fee_rollover()
RETURNS TABLE(enrollment_id UUID, month_key TEXT, status TEXT, advance_used NUMERIC)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_current_month TEXT := to_char(NOW() AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM');
  v_enrollment    RECORD;
  v_class_fee     NUMERIC;
  v_adv_balance   NUMERIC;
  v_applied_adv   NUMERIC;
  v_paid_amt      NUMERIC;
  v_new_status    TEXT;
BEGIN
  FOR v_enrollment IN
    SELECT
      e.id          AS enrollment_id,
      e.advance_balance,
      tc.fee_amount
    FROM enrollments e
    JOIN teacher_classes tc ON tc.id = e.class_id
    WHERE e.status = 'approved'
      AND e.deleted_at IS NULL
  LOOP

    -- Skip if fee record already exists for this month
    IF EXISTS (
      SELECT 1 FROM fee_records f
      WHERE f.enrollment_id = v_enrollment.enrollment_id
        AND f.month_key = v_current_month
    ) THEN
      CONTINUE;
    END IF;

    v_class_fee   := COALESCE(v_enrollment.fee_amount, 0);
    v_adv_balance := COALESCE(v_enrollment.advance_balance, 0);
    v_applied_adv := LEAST(v_adv_balance, v_class_fee);
    v_paid_amt    := v_applied_adv;

    v_new_status := CASE
      WHEN v_class_fee = 0              THEN 'paid'
      WHEN v_applied_adv >= v_class_fee THEN 'paid'
      WHEN v_applied_adv > 0           THEN 'partial'
      ELSE                                   'pending'
    END;

    -- Create the fee record for this month
    INSERT INTO fee_records (enrollment_id, month_key, total_amount, paid_amount, status)
    VALUES (v_enrollment.enrollment_id, v_current_month, v_class_fee, v_paid_amt, v_new_status);

    -- Deduct used advance from enrollment
    IF v_applied_adv > 0 THEN
      UPDATE enrollments
      SET advance_balance = v_adv_balance - v_applied_adv
      WHERE id = v_enrollment.enrollment_id;
    END IF;

    -- Return this row for logging/debugging
    enrollment_id := v_enrollment.enrollment_id;
    month_key     := v_current_month;
    status        := v_new_status;
    advance_used  := v_applied_adv;
    RETURN NEXT;

  END LOOP;
END;
$$;

-- ── 2. Test the function manually (see results before scheduling) ──
-- Run this to verify the function works on your data:
-- SELECT * FROM do_monthly_fee_rollover();

-- ── 3. Schedule via pg_cron ──────────────────────────────────────────────────
-- Supabase supports pg_cron. Enable it in the Dashboard:
--   Dashboard → Database → Extensions → enable "pg_cron"
-- Then run the schedule below:

-- Remove existing schedule if any
SELECT cron.unschedule('monthly-fee-rollover') WHERE EXISTS (
  SELECT 1 FROM cron.job WHERE jobname = 'monthly-fee-rollover'
);

-- Schedule: runs at 00:30 UTC on the 1st of every month (= 6:00 AM IST)
SELECT cron.schedule(
  'monthly-fee-rollover',   -- job name
  '30 0 1 * *',             -- cron expression: min hour day month weekday
  'SELECT do_monthly_fee_rollover()'
);

-- Verify the schedule was created:
SELECT jobname, schedule, command, active FROM cron.job WHERE jobname = 'monthly-fee-rollover';

SELECT 'Migration 05 complete ✅' AS status;
