-- Create the 'chat_attachments' bucket if it doesn't exist
INSERT INTO storage.buckets (id, name, public)
VALUES ('chat_attachments', 'chat_attachments', true)
ON CONFLICT (id) DO NOTHING;

-- Set up RLS policies for 'chat_attachments' bucket
-- Allow public read access to chat attachments
CREATE POLICY "Public Access" ON storage.objects
FOR SELECT
USING (bucket_id = 'chat_attachments');

-- Allow authenticated users to upload chat attachments
CREATE POLICY "Authenticated users can upload attachments" ON storage.objects
FOR INSERT
WITH CHECK (
    bucket_id = 'chat_attachments' AND auth.role() = 'authenticated'
);

-- Allow authenticated users to delete their own attachments
CREATE POLICY "Users can delete their own attachments" ON storage.objects
FOR DELETE
USING (
    bucket_id = 'chat_attachments' AND auth.uid() = owner
);
