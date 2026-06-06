-- ============================================================================
-- FEES MANAGER v8 MIGRATION — Run this in Supabase SQL Editor
-- Fixes: join_academy RPC not saving the student's class correctly
-- ============================================================================

DROP FUNCTION IF EXISTS join_academy(TEXT, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION join_academy(
  p_name      TEXT,
  p_class     TEXT,
  p_whatsapp  TEXT,
  p_join_code TEXT
)
RETURNS JSON AS $$
DECLARE
  v_student_id    UUID := auth.uid();
  v_teacher_id    UUID;
  v_class_id      UUID;
  v_fee_amount    NUMERIC := 0;
  v_enrollment_id UUID;
  v_status        TEXT;
  v_month_key     TEXT;
BEGIN
  IF v_student_id IS NULL THEN
    RETURN json_build_object('success', false, 'error', 'Not authenticated');
  END IF;

  -- 1. Find teacher by join code
  SELECT id INTO v_teacher_id
  FROM teachers
  WHERE join_code = upper(p_join_code);

  IF v_teacher_id IS NULL THEN
    RETURN json_build_object('success', false, 'error', 'Invalid join code');
  END IF;

  -- 2. Update student profile
  UPDATE profiles
  SET full_name = p_name, role = 'student'
  WHERE id = v_student_id;

  -- 3. Check if already enrolled
  SELECT id, status INTO v_enrollment_id, v_status
  FROM enrollments
  WHERE student_id = v_student_id 
    AND teacher_id = v_teacher_id 
    AND deleted_at IS NULL;

  IF v_enrollment_id IS NOT NULL THEN
    RETURN json_build_object(
      'success', true, 
      'already_enrolled', true, 
      'status', v_status,
      'teacher_id', v_teacher_id
    );
  END IF;

  -- 4. Find or create class in teacher_classes
  SELECT id, fee_amount INTO v_class_id, v_fee_amount
  FROM teacher_classes
  WHERE teacher_id = v_teacher_id AND class_name = p_class;

  IF v_class_id IS NULL THEN
    INSERT INTO teacher_classes (teacher_id, class_name, fee_amount)
    VALUES (v_teacher_id, p_class, 0)
    RETURNING id, fee_amount INTO v_class_id, v_fee_amount;
  END IF;

  -- 5. Create pending enrollment
  INSERT INTO enrollments (student_id, teacher_id, class_id, status, whatsapp_number)
  VALUES (v_student_id, v_teacher_id, v_class_id, 'pending', p_whatsapp)
  RETURNING id INTO v_enrollment_id;

  -- 6. Create fee record for current month
  v_month_key := to_char(NOW(), 'YYYY-MM');
  INSERT INTO fee_records (enrollment_id, month_key, total_amount, paid_amount, status)
  VALUES (
    v_enrollment_id, v_month_key, v_fee_amount, 0,
    CASE WHEN v_fee_amount > 0 THEN 'pending' ELSE 'paid' END
  );

  RETURN json_build_object(
    'success', true, 
    'already_enrolled', false, 
    'status', 'pending',
    'teacher_id', v_teacher_id
  );

EXCEPTION WHEN OTHERS THEN
  RETURN json_build_object('success', false, 'error', SQLERRM);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;

SELECT 'Migration complete ✅' AS status;
