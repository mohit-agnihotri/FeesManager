-- ============================================================================
-- Migration 06: Add advance_amount to payments table
-- Run this in Supabase SQL Editor
--
-- This column tracks exactly how much of each payment went to advance balance.
-- Fixes the bug where the Advance Balance screen showed ALL payments as
-- "advance paid" entries instead of only the excess (overpaid) amount.
-- ============================================================================

ALTER TABLE payments ADD COLUMN IF NOT EXISTS advance_amount NUMERIC DEFAULT 0;

-- Backfill existing rows: set advance_amount = 0 (safe default, old data is ambiguous)
UPDATE payments SET advance_amount = 0 WHERE advance_amount IS NULL;

SELECT 'Migration 06 complete ✅ — advance_amount column added to payments' AS status;
