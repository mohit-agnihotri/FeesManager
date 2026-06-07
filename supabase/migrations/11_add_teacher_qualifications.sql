-- Add qualifications field to teachers table for Edit Profile functionality

ALTER TABLE teachers ADD COLUMN IF NOT EXISTS qualifications TEXT;
