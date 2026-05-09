# Note Sphere (formerly AI Study Assistant)

A powerful, native Android application designed for students to manage study materials and get AI-powered insights (summaries, notes, practice questions, and document Q&A).

## Tech Stack
- **Android Client**: Java, ViewBinding, Retrofit, Firebase SDK, Glide, Markwon
- **Backend**: Node.js, Express, Firebase Admin SDK, Google Gemini API
- **Infrastructure**: Firebase (Firestore, Cloud Storage, Authentication)
- **AI Engine**: Google Gemini Pro (Text & Vision)

## Key Features
- **Firebase Ecosystem**: Unified authentication, real-time database, and scalable cloud storage.
- **AI Study Tools**: 
  - **AI Summaries**: Condense long documents into key points.
  - **AI Quizzes**: Generate practice questions from your study materials.
  - **AI Doubt Solver**: Ask questions about your PDFs/images and get instant answers.
- **Syllabus Tracking**: Track your progress against your university syllabus.
- **Offline-First**: Documents are saved locally for access without internet.
- **Premium UI**: Modern Material Design with a custom "Note Sphere" aesthetic.

## Setup Instructions

### 1. Firebase Setup
1. Create a new project on [Firebase Console](https://console.firebase.google.com).
2. Register an Android App with package name `com.notesphere.app`.
3. Download `google-services.json` and place it in the `android/app/` directory.
4. Enable **Email/Password** and **Google Sign-In** in the Authentication tab.
5. Create a **Firestore** database in test mode.
6. Create a **Cloud Storage** bucket.

### 2. Backend Setup
1. Go to the `backend/` directory.
2. Create a `.env` file with:
   ```
   PORT=5000
   FIREBASE_PROJECT_ID=your_project_id
   FIREBASE_CLIENT_EMAIL=your_service_account_email
   FIREBASE_PRIVATE_KEY="your_private_key"
   GEMINI_API_KEY=your_gemini_api_key
   ```
3. Run `npm install` and `npm run dev`.

### 3. Android Setup
1. Open the `android/` folder in Android Studio.
2. Sync Project with Gradle Files.
3. Run the app on an emulator or physical device.

## Progress
- [x] Full Migration from Supabase to Firebase
- [x] Professional Package Rebrand (`com.notesphere.app`)
- [x] Integrated Google Gemini Pro for AI features
- [x] Modern Dashboard & Material Management
- [/] AI Quiz & Doubt Solver UI (Phase 2)
- [ ] Syllabus Progress Tracking (Phase 2)
