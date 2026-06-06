-- Migration: Add deleted_by column to messages for "Delete for me" feature

ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_by TEXT[] DEFAULT '{}';

-- Allow users to update deleted_by in the messages table
DROP POLICY IF EXISTS "Users can update deleted_by" ON messages;
CREATE POLICY "Users can update deleted_by" 
ON messages FOR UPDATE
USING (auth.uid() = student_id OR auth.uid() = teacher_id)
WITH CHECK (auth.uid() = student_id OR auth.uid() = teacher_id);

-- Wait, actually we also need to allow the teacher or sender to completely DELETE the message
DROP POLICY IF EXISTS "Users can delete own messages" ON messages;
CREATE POLICY "Users can delete own messages"
ON messages FOR DELETE
USING (auth.uid()::text = sender_id);
