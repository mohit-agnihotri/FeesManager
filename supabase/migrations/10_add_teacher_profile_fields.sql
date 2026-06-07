-- Add phone and quote fields to teachers table for Edit Profile functionality

ALTER TABLE teachers ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE teachers ADD COLUMN IF NOT EXISTS quote TEXT;

-- Update the get_teacher_profile and other related RPCs if they select * or specific columns, but standard select * will automatically include them.
