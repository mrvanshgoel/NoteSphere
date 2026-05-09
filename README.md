# 🚀 NoteSphere AI — The Study Operating System

NoteSphere AI is an AI-native study ecosystem inspired by **NotebookLM**, designed to transform how students interact with their learning materials. It's not just a collection of tools; it's a "Second Brain" that proactively helps you retain, revise, and master your subjects.

---

## 🧠 Core Pillars of Intelligence

### 1. ⚡ Study Intelligence Hub (Phase 1)
NoteSphere doesn't just wait for you; it's proactive. It tracks your **weak topics** from quizzes, monitors your **study consistency**, and generates **intelligent reminders**. 
*   "You struggled with DBMS Normalization last week. Time for a quick recap?"
*   "You haven't revised OS Unit 3 in 5 days."

### 2. 🗂️ AI-Native Flashcards (Phase 2 & 3)
Transform any PDF or image into a high-quality study deck instantly.
*   **Swipe UI**: Interactive cards with flip animations.
*   **Active Recall**: Mark cards as mastered to focus on what you don't know.

### 3. 💬 Context-Aware AI Chat
Chat with your documents like never before. NoteSphere uses **RAG (Retrieval-Augmented Generation)** to answer questions based specifically on your uploaded materials.
*   **Multi-Modal**: Supports PDFs and Images (Native OCR via Gemini Vision).
*   **Persistent Sessions**: Chat history saved automatically to Firestore.

### 4. 🛠️ Study Analytics
Real-time tracking of your academic journey.
*   **Study Streaks**: Stay consistent.
*   **Accuracy Tracking**: Watch your understanding grow through quiz analytics.
*   **Syllabus Progress**: Visualize how much of your course is complete.

---

## 🛠️ Technical Architecture

### **Mobile (Android)**
*   **Language**: Java / XML
*   **Design**: Material Design 3 (Dark Mode Optimized)
*   **Networking**: Retrofit 2 with GSON
*   **Image Processing**: Glide

### **Backend (Node.js)**
*   **Runtime**: Express.js
*   **AI Engine**: Google Gemini API (Dynamic Fallback System)
*   **Auth & Database**: Firebase (Auth, Firestore, Admin SDK)
*   **OCR & Parsing**: Gemini Vision + pdf-parse (Robust Implementation)

---

## 👨‍💻 Developer
Created with ❤️ by **Vansh Goel**.
*   **Age**: 19 (Born May 30, 2006)
*   **Academic**: BCA (Hons. with Research) in Multimedia & Animation, **Galgotias University**.
*   **Profile**: Tech & creative enthusiast, Freelance UI/UX & Graphic Designer.
*   **Origin**: Meerut, Uttar Pradesh, India.

---

## 🚀 Getting Started

### 1. Backend Setup
1. `cd backend`
2. `npm install`
3. Create `.env` with `FIREBASE_SERVICE_ACCOUNT` (JSON) and `GEMINI_API_KEY`.
4. `npm start`

### 2. Android Setup
1. Open `android/` in Android Studio.
2. Add your `google-services.json` from Firebase.
3. Update `BASE_URL` in `ApiClient.java` to your server.
4. Build & Run.

---

*NoteSphere AI is a continuous evolution. We are doubling down on Source-Aware responses and Multi-Document context in the upcoming phases.*
