-- ============================================================================
-- Fix: Allow students to update their own profile and whatsapp number
-- ============================================================================

-- 1. Allow users to update their own profile (full_name, email, avatar_url)
DROP POLICY IF EXISTS "Users can update own profile" ON profiles;
CREATE POLICY "Users can update own profile"
ON profiles
FOR UPDATE
USING (id = auth.uid())
WITH CHECK (id = auth.uid());

-- 2. Allow students to update their own whatsapp_number in their enrollments
DROP POLICY IF EXISTS "Students can update own enrollments" ON enrollments;
CREATE POLICY "Students can update own enrollments"
ON enrollments
FOR UPDATE
USING (student_id = auth.uid())
WITH CHECK (student_id = auth.uid());
