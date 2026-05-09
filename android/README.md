# AI Study Assistant - Native Android App

This is a native Android application built with Java, following the Material Design 3 guidelines.

## Features
- **Splash Screen**: Branded entry point.
- **Auth**: Email/Password Login & Registration with JWT.
- **Home**: Dashboard with subjects and recent activity.
- **Subjects**: Grid view of all subjects.
- **Upload**: Pick files (PDF, Images) and upload to the backend.
- **Material Details**: AI-powered Summary, Notes, and Q&A generation.
- **AI Chat**: Interactive chat interface for study queries.
- **Profile**: User management and logout.

## Tech Stack
- **Language**: Java
- **Networking**: Retrofit2 + OkHttp3
- **Image Loading**: Glide
- **JSON Parsing**: Gson
- **UI**: Material Components, ViewBinding, ConstraintLayout, RecyclerView.

## Setup Instructions
1. Open the `android` folder in Android Studio.
2. Ensure you have Android SDK 34 installed.
3. Sync Project with Gradle Files.
4. Run on an emulator or physical device (Min SDK 24).

## API Backend
The app connects to: `https://vansh-project.onrender.com/`
Make sure the backend is running for full functionality.
