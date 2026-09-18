-- ==============================================================================
-- Aurio "Listen Together" Database Schema for Supabase
-- Run this script in the Supabase SQL Editor (https://supabase.com/dashboard)
-- ==============================================================================

-- 1. Create Rooms Table
CREATE TABLE IF NOT EXISTS public.rooms (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT true,
    host_id TEXT NOT NULL,
    host_name TEXT NOT NULL,
    code TEXT NOT NULL,
    tags JSONB DEFAULT '[]'::jsonb,
    image_url TEXT DEFAULT '',
    tagline TEXT DEFAULT 'Same Music More Vibes',
    current_song_id TEXT DEFAULT '',
    current_song_title TEXT DEFAULT '',
    current_song_artist TEXT DEFAULT '',
    current_song_thumbnail TEXT DEFAULT '',
    is_playing BOOLEAN DEFAULT false,
    playback_position_ms BIGINT DEFAULT 0,
    last_sync_timestamp BIGINT DEFAULT 0,
    listener_count INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- 2. Create Room Participants Table
CREATE TABLE IF NOT EXISTS public.room_participants (
    id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    user_name TEXT NOT NULL,
    avatar_url TEXT DEFAULT '',
    is_host BOOLEAN DEFAULT false,
    is_online BOOLEAN DEFAULT true,
    is_audio_enabled BOOLEAN DEFAULT true,
    is_mic_enabled BOOLEAN DEFAULT false,
    last_seen TIMESTAMPTZ DEFAULT now()
);

-- 3. Create Room Messages Table
CREATE TABLE IF NOT EXISTS public.room_messages (
    id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
    sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    avatar_url TEXT DEFAULT '',
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 4. Create Room Song Requests Table (Play Next / Add to Queue Host Requests)
CREATE TABLE IF NOT EXISTS public.room_song_requests (
    id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
    song_id TEXT NOT NULL,
    song_title TEXT NOT NULL,
    song_artist TEXT NOT NULL,
    song_thumbnail TEXT DEFAULT '',
    requester_id TEXT NOT NULL,
    requester_name TEXT NOT NULL,
    request_type TEXT NOT NULL, -- 'PLAY_NEXT' or 'ADD_TO_QUEUE'
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'DECLINED'
    created_at BIGINT DEFAULT 0
);

-- 5. Create Indexes for High-Performance Queries
CREATE INDEX IF NOT EXISTS idx_rooms_is_public ON public.rooms(is_public);
CREATE INDEX IF NOT EXISTS idx_rooms_code ON public.rooms(code);
CREATE INDEX IF NOT EXISTS idx_rooms_host_id ON public.rooms(host_id);
CREATE INDEX IF NOT EXISTS idx_participants_room_id ON public.room_participants(room_id);
CREATE INDEX IF NOT EXISTS idx_participants_user_id ON public.room_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_messages_room_id ON public.room_messages(room_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON public.room_messages(created_at);
CREATE INDEX IF NOT EXISTS idx_song_requests_room_id ON public.room_song_requests(room_id);
CREATE INDEX IF NOT EXISTS idx_song_requests_status ON public.room_song_requests(status);

-- 6. Enable Row-Level Security (RLS) with permissive read/write for app users
ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_song_requests ENABLE ROW LEVEL SECURITY;

-- Allow public / authenticated access to rooms
CREATE POLICY "Allow public read on rooms" ON public.rooms FOR SELECT USING (true);
CREATE POLICY "Allow insert on rooms" ON public.rooms FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow update on rooms" ON public.rooms FOR UPDATE USING (true);
CREATE POLICY "Allow delete on rooms" ON public.rooms FOR DELETE USING (true);

-- Allow public / authenticated access to participants
CREATE POLICY "Allow public read on participants" ON public.room_participants FOR SELECT USING (true);
CREATE POLICY "Allow insert/upsert on participants" ON public.room_participants FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow update on participants" ON public.room_participants FOR UPDATE USING (true);
CREATE POLICY "Allow delete on participants" ON public.room_participants FOR DELETE USING (true);

-- Allow public / authenticated access to messages
CREATE POLICY "Allow public read on messages" ON public.room_messages FOR SELECT USING (true);
CREATE POLICY "Allow insert on messages" ON public.room_messages FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow delete on messages" ON public.room_messages FOR DELETE USING (true);

-- Allow public / authenticated access to song requests
CREATE POLICY "Allow public read on song requests" ON public.room_song_requests FOR SELECT USING (true);
CREATE POLICY "Allow insert on song requests" ON public.room_song_requests FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow update on song requests" ON public.room_song_requests FOR UPDATE USING (true);
CREATE POLICY "Allow delete on song requests" ON public.room_song_requests FOR DELETE USING (true);

-- 7. Enable Realtime Replication for Instant Event Broadcasting
ALTER PUBLICATION supabase_realtime ADD TABLE public.rooms;
ALTER PUBLICATION supabase_realtime ADD TABLE public.room_participants;
ALTER PUBLICATION supabase_realtime ADD TABLE public.room_messages;
ALTER PUBLICATION supabase_realtime ADD TABLE public.room_song_requests;
