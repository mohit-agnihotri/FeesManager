-- ============================================================================
-- FIX: Create missing enrollment for existing students
-- Run this in the Supabase SQL Editor
-- ============================================================================

-- Student IDs from profiles:
-- chef: 0ca87e62-534b-4291-8eee-43cafd096bb4
-- Teacher: shraddha khapra (apna college): 2dc676a2-8cb7-41c4-9fd0-591cdda0de58
-- Class 12 for apna college: 115e5025-8b7b-4dfe-b54c-476be6efa023 (fee: 1200)

-- Step 1: Create enrollment for chef with approved status
INSERT INTO enrollments (student_id, teacher_id, class_id, status, whatsapp_number)
VALUES (
  '0ca87e62-534b-4291-8eee-43cafd096bb4',  -- chef
  '2dc676a2-8cb7-41c4-9fd0-591cdda0de58',  -- shraddha khapra
  '115e5025-8b7b-4dfe-b54c-476be6efa023',  -- Class 12 (1200/month)
  'approved',
  '9999999999'
)
ON CONFLICT DO NOTHING
RETURNING id;

-- Step 2: Create fee records for recent months (Apr, May, Jun 2026)
-- After Step 1 runs, get the enrollment_id and insert fee records
DO $$
DECLARE
  v_enrollment_id UUID;
BEGIN
  SELECT id INTO v_enrollment_id
  FROM enrollments
  WHERE student_id = '0ca87e62-534b-4291-8eee-43cafd096bb4'
    AND teacher_id = '2dc676a2-8cb7-41c4-9fd0-591cdda0de58'
    AND deleted_at IS NULL;

  IF v_enrollment_id IS NULL THEN
    RAISE NOTICE 'Enrollment not found!';
    RETURN;
  END IF;

  RAISE NOTICE 'Enrollment ID: %', v_enrollment_id;

  -- April 2026 - create as paid (student paid 1200 earlier)
  INSERT INTO fee_records (enrollment_id, month_key, total_amount, paid_amount, status)
  VALUES (v_enrollment_id, '2026-04', 1200, 1200, 'paid')
  ON CONFLICT DO NOTHING;

  -- May 2026 - pending
  INSERT INTO fee_records (enrollment_id, month_key, total_amount, paid_amount, status)
  VALUES (v_enrollment_id, '2026-05', 1200, 0, 'pending')
  ON CONFLICT DO NOTHING;

  -- June 2026 - pending (current month)
  INSERT INTO fee_records (enrollment_id, month_key, total_amount, paid_amount, status)
  VALUES (v_enrollment_id, '2026-06', 1200, 0, 'pending')
  ON CONFLICT DO NOTHING;

  RAISE NOTICE 'Fee records created for enrollment: %', v_enrollment_id;
END;
$$;

-- Verify
SELECT 
  e.id as enrollment_id,
  e.status,
  p.full_name as student_name,
  t.academy_name,
  tc.class_name,
  tc.fee_amount
FROM enrollments e
JOIN profiles p ON p.id = e.student_id
JOIN teachers t ON t.id = e.teacher_id
JOIN teacher_classes tc ON tc.id = e.class_id;

SELECT * FROM fee_records ORDER BY month_key DESC LIMIT 10;
