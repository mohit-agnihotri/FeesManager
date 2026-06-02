-- ============================================================================
-- FEES MANAGER v2 MIGRATION — Run this in Supabase SQL Editor
-- Fixes: Chat, Manual Add Student, Join Code Regeneration, Announcements Class Filter
-- ============================================================================

-- ── 1. MESSAGES TABLE (Personal + Class Chat) ─────────────────────────────

CREATE TABLE IF NOT EXISTS messages (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  teacher_id UUID REFERENCES teachers(id) ON DELETE CASCADE NOT NULL,
  student_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
  class_name TEXT,
  chat_type TEXT CHECK (chat_type IN ('personal', 'class')) NOT NULL,
  sender_id TEXT NOT NULL,
  sender_name TEXT NOT NULL,
  text TEXT NOT NULL,
  attachment_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

-- Teacher can read/write all messages in their academy
DROP POLICY IF EXISTS "Messages teacher all" ON messages;
CREATE POLICY "Messages teacher all"
ON messages FOR ALL
USING (auth.uid() = teacher_id)
WITH CHECK (auth.uid() = teacher_id);

-- Students can read/write messages in their own personal chat
DROP POLICY IF EXISTS "Messages student personal" ON messages;
CREATE POLICY "Messages student personal"
ON messages FOR ALL
USING (
  (chat_type = 'personal' AND auth.uid() = student_id)
  OR
  (chat_type = 'class' AND EXISTS (
    SELECT 1 FROM enrollments e
    JOIN teacher_classes tc ON tc.id = e.class_id
    WHERE e.student_id = auth.uid()
    AND e.teacher_id = messages.teacher_id
    AND tc.class_name = messages.class_name
    AND e.deleted_at IS NULL
  ))
)
WITH CHECK (
  (chat_type = 'personal' AND auth.uid() = student_id)
  OR
  (chat_type = 'class' AND EXISTS (
    SELECT 1 FROM enrollments e
    JOIN teacher_classes tc ON tc.id = e.class_id
    WHERE e.student_id = auth.uid()
    AND e.teacher_id = messages.teacher_id
    AND tc.class_name = messages.class_name
    AND e.deleted_at IS NULL
  ))
);

-- Enable realtime for messages
ALTER PUBLICATION supabase_realtime ADD TABLE messages;

-- ── 2. ADD target_class to announcements ─────────────────────────────────

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS target_class TEXT DEFAULT 'all';

-- ── 3. RPC: add_student_manually ────────────────────────────────────────

DROP FUNCTION IF EXISTS add_student_manually(TEXT, TEXT, TEXT, NUMERIC);
CREATE OR REPLACE FUNCTION add_student_manually(
  p_name     TEXT,
  p_class    TEXT,
  p_phone    TEXT,
  p_fee      NUMERIC DEFAULT NULL
)
RETURNS JSON AS $$
DECLARE
  v_teacher_id    UUID := auth.uid();
  v_student_id    UUID := gen_random_uuid();
  v_email         TEXT := 'manual_' || replace(v_student_id::text, '-', '') || '@feesmanager.local';
  v_class_id      UUID;
  v_fee_amount    NUMERIC := 0;
  v_enrollment_id UUID;
  v_month_key     TEXT;
BEGIN
  IF v_teacher_id IS NULL THEN
    RETURN json_build_object('success', false, 'error', 'Not authenticated');
  END IF;

  -- Step 1: Create auth.users entry (SECURITY DEFINER allows access to auth schema)
  INSERT INTO auth.users (
    id, instance_id, email, encrypted_password,
    email_confirmed_at, created_at, updated_at,
    raw_app_meta_data, raw_user_meta_data,
    is_super_admin, role, aud
  ) VALUES (
    v_student_id,
    '00000000-0000-0000-0000-000000000000',
    v_email,
    '',
    NOW(), NOW(), NOW(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    json_build_object('full_name', p_name, 'phone', p_phone)::jsonb,
    false, 'authenticated', 'authenticated'
  );

  -- Step 2: Create profile
  INSERT INTO profiles (id, email, full_name, role)
  VALUES (v_student_id, v_email, p_name, 'student')
  ON CONFLICT (id) DO UPDATE SET full_name = p_name, role = 'student';

  -- Step 3: Find or create class
  SELECT id, fee_amount INTO v_class_id, v_fee_amount
  FROM teacher_classes
  WHERE teacher_id = v_teacher_id AND class_name = p_class;

  IF v_class_id IS NULL THEN
    INSERT INTO teacher_classes (teacher_id, class_name, fee_amount)
    VALUES (v_teacher_id, p_class, COALESCE(p_fee, 0))
    RETURNING id, fee_amount INTO v_class_id, v_fee_amount;
  END IF;

  -- Use provided fee if given, otherwise use class default
  v_fee_amount := COALESCE(p_fee, v_fee_amount, 0);

  -- Step 4: Create enrollment (immediately approved since teacher is adding)
  INSERT INTO enrollments (student_id, teacher_id, class_id, status, whatsapp_number)
  VALUES (v_student_id, v_teacher_id, v_class_id, 'approved', p_phone)
  RETURNING id INTO v_enrollment_id;

  -- Step 5: Create fee record for current month
  v_month_key := to_char(NOW(), 'YYYY-MM');
  INSERT INTO fee_records (enrollment_id, month_key, total_amount, paid_amount, status)
  VALUES (
    v_enrollment_id, v_month_key, v_fee_amount, 0,
    CASE WHEN v_fee_amount > 0 THEN 'pending' ELSE 'paid' END
  );

  RETURN json_build_object('success', true, 'student_id', v_student_id, 'email', v_email);

EXCEPTION WHEN OTHERS THEN
  RETURN json_build_object('success', false, 'error', SQLERRM);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;

-- ── 4. RPC: regenerate_join_code ─────────────────────────────────────────

DROP FUNCTION IF EXISTS regenerate_join_code();
CREATE OR REPLACE FUNCTION regenerate_join_code()
RETURNS JSON AS $$
DECLARE
  v_teacher_id UUID := auth.uid();
  v_new_code   TEXT;
  v_attempts   INT := 0;
BEGIN
  IF v_teacher_id IS NULL THEN
    RETURN json_build_object('success', false, 'error', 'Not authenticated');
  END IF;

  LOOP
    v_new_code := upper(substring(md5(random()::text) from 1 for 6));
    v_attempts := v_attempts + 1;
    EXIT WHEN NOT EXISTS (SELECT 1 FROM teachers WHERE join_code = v_new_code);
    IF v_attempts > 20 THEN
      RETURN json_build_object('success', false, 'error', 'Could not generate unique code');
    END IF;
  END LOOP;

  UPDATE teachers SET join_code = v_new_code WHERE id = v_teacher_id;

  RETURN json_build_object('success', true, 'new_code', v_new_code);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ── 5. RPC: get_pending_fee_for_student ────────────────────────────────

DROP FUNCTION IF EXISTS get_pending_fee_for_student(UUID);
CREATE OR REPLACE FUNCTION get_pending_fee_for_student(p_student_id UUID)
RETURNS JSON AS $$
DECLARE
  v_teacher_id    UUID := auth.uid();
  v_enrollment_id UUID;
  v_month_key     TEXT := to_char(NOW(), 'YYYY-MM');
  v_fee_record    RECORD;
  v_advance       NUMERIC := 0;
  v_pending       NUMERIC := 0;
BEGIN
  SELECT id, advance_balance INTO v_enrollment_id, v_advance
  FROM enrollments
  WHERE student_id = p_student_id AND teacher_id = v_teacher_id AND deleted_at IS NULL;

  IF v_enrollment_id IS NULL THEN
    RETURN json_build_object('success', false, 'error', 'Enrollment not found');
  END IF;

  SELECT * INTO v_fee_record FROM fee_records
  WHERE enrollment_id = v_enrollment_id AND month_key = v_month_key;

  IF v_fee_record IS NULL THEN
    -- Get class fee
    SELECT tc.fee_amount INTO v_pending
    FROM enrollments e
    JOIN teacher_classes tc ON tc.id = e.class_id
    WHERE e.id = v_enrollment_id;
    v_pending := COALESCE(v_pending, 0);
  ELSE
    v_pending := GREATEST(0, v_fee_record.total_amount - v_fee_record.paid_amount);
  END IF;

  -- Apply advance balance
  RETURN json_build_object(
    'success', true,
    'pending', v_pending,
    'advance', v_advance,
    'net_pending', GREATEST(0, v_pending - v_advance),
    'month_key', v_month_key
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ── 6. Enable realtime for fee_records and payments ──────────────────────

DO $$
BEGIN
  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE fee_records;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE payments;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE enrollments;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
  BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE announcements;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
END $$;

-- ── Done ──────────────────────────────────────────────────────────────────
SELECT 'Migration complete ✅' AS status;
