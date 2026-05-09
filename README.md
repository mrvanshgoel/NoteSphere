# AI Study Assistant

An Android-style, full-stack web application designed for students to manage study materials and get AI-powered insights (summaries, notes, practice questions, and document Q&A).

## Tech Stack
- **Frontend**: React, Tailwind CSS, Framer Motion, Vite
- **Backend**: Node.js, Express, Anthropic API (Claude 3.5 Sonnet)
- **Database & Storage**: Supabase (PostgreSQL, Storage, Auth)

## Setup Instructions

### 1. Supabase Setup
1. Create a new project on [Supabase](https://supabase.com).
2. Go to **Authentication** and enable **Email/Password** sign-in.
3. Go to **SQL Editor** and run the following commands to create your database tables:

```sql
-- Create subjects table
CREATE TABLE subjects (
  id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  color TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create materials table
CREATE TABLE materials (
  id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  subject_id UUID REFERENCES subjects(id) ON DELETE CASCADE,
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  file_url TEXT NOT NULL,
  storage_path TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Row Level Security (RLS)
ALTER TABLE subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE materials ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own subjects" ON subjects FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own subjects" ON subjects FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own subjects" ON subjects FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own subjects" ON subjects FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can view own materials" ON materials FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own materials" ON materials FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own materials" ON materials FOR DELETE USING (auth.uid() = user_id);
```

4. Go to **Storage** and create a new bucket named `study_materials`.
   - Set the bucket to **Public**.
   - Under Configuration -> Policies, allow authenticated users to SELECT and INSERT.

### 2. Environment Variables

**Frontend (`frontend/.env.local`):**
```
VITE_SUPABASE_URL=your_supabase_project_url
VITE_SUPABASE_ANON_KEY=your_supabase_anon_key
```

**Backend (`backend/.env`):**
```
PORT=5000
SUPABASE_URL=your_supabase_project_url
SUPABASE_ANON_KEY=your_supabase_anon_key
ANTHROPIC_API_KEY=your_anthropic_api_key
```

### 3. Running the App Locally

**Start the Backend:**
```bash
cd backend
npm run dev
```

**Start the Frontend:**
```bash
cd frontend
npm run dev
```

## Features Complete
- [x] Multi-Student Login System via Supabase
- [x] Subject-wise folder creation
- [x] Uploading files directly to Supabase storage
- [x] AI-powered Chat & Q&A
- [x] Summary, Notes & Practice quiz generation
- [x] Smooth, dark-theme UI with Android/mobile-first feel
