# 🚀 NoteSphere — The Generative AI Study Operating System

![NoteSphere Banner](https://img.shields.io/badge/Status-Stable_Release_v1.0-brightgreen) ![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android) ![Backend](https://img.shields.io/badge/Backend-Node.js-339933?logo=nodedotjs) ![AI](https://img.shields.io/badge/AI_Engine-Google_Gemini-8E75B2?logo=google)

NoteSphere is a unified, intelligent "Study OS" that eliminates the friction of modern academic workflows. It bridges traditional file management with advanced Generative AI capabilities, allowing students to upload materials (PDFs, Images) and instantly generate structured summaries, interactive flashcards, quizzes, and contextual AI chats based directly on their syllabus.

---

## 🧠 Core Architecture & Features

### 1. 🗂️ Infinite Hierarchical File Explorer
Unlike rigid flat-file systems, NoteSphere provides a native file-system experience.
- **Infinite Nesting**: Create folders within folders recursively using a self-referencing NoSQL schema in Firestore.
- **Breadcrumb Navigation**: Seamlessly traverse deep folder structures.
- **Context Actions**: Long-press any folder or material to rename, share, or delete.

### 2. ⚡ Custom AI Orchestration Layer (`ai_router.js`)
NoteSphere doesn't just hardcode a single LLM call; it features a custom-built AI Router that guarantees high availability and robust performance.
- **Task-Based Routing**: Dynamically routes requests depending on the task type (e.g., `OCR` for vision extraction, `DOCUMENT` for structured JSON output, `CHAT` for conversational Q&A).
- **Fallback Chaining**: If a primary model (like `gemini-3.1-pro-preview`) hits a rate limit or goes down, the router automatically catches the exception and falls back to a highly available alternative (like `gemini-2.5-flash`) within milliseconds.
- **Dynamic Discovery**: On backend boot, the router queries the Gemini API to discover live, supported models, preventing requests to deprecated endpoints.

### 3. 💬 Context-Aware AI Tutor
Chat directly with your academic documents.
- **Multi-Modal**: Upload scanned handwritten notes, diagrams, or dense PDFs. NoteSphere uses native OCR via Gemini Vision to extract the text.
- **Auto-Titling**: Chat sessions automatically generate concise titles based on your initial prompt.
- **Orphan Cleanup**: Empty chat sessions are intelligently cleaned up to maintain a pristine history.

### 4. 📚 Automated Study Artifact Generation
Turn passive reading into active mastery.
- **Smart Notes**: Generates comprehensive, markdown-formatted study notes from uploaded materials.
- **Flashcard Deck Generator**: Instantly creates interactive front/back flashcards for active recall.
- **Quiz Generator**: Generates multiple-choice quizzes with adjustable difficulty levels (Easy/Medium/Hard) to test comprehension and application.

---

## 🛠️ Technology Stack

### **Frontend (Native Android)**
*   **Language**: Java / XML
*   **Design**: Material Design 3 (Dark Mode Optimized with custom `ns_purple` and `ns_surface` themes)
*   **Networking**: Retrofit 2 with GSON serialization
*   **Media & Rendering**: Glide for image loading, Markwon for native Markdown rendering

### **Backend (Node.js REST API)**
*   **Runtime & Framework**: Node.js (v18+) with Express.js
*   **AI Engine**: Google Generative AI (Gemini SDK)
*   **Database**: Google Firebase (Firestore NoSQL)
*   **Authentication**: Firebase Auth (JWT verification via `firebase-admin`)
*   **File Handling**: Multer for multipart uploads, `pdf-parse` for fallback extraction

### **Deployment Pipeline**
*   **Hosting**: Render.com Web Services
*   **Storage Strategy**: Ephemeral local disk storage for physical files, backed by persistent text caching in Firestore.

---

## 👨‍💻 Developer
Designed and engineered by **Vansh Goel**.
*   **Academic**: BCA (Hons. with Research) in Multimedia & Animation, **Galgotias University**.
*   **Profile**: Technical architect, creative enthusiast, and freelance UI/UX & Graphic Designer.

---

## 🚀 Getting Started

### 1. Backend Setup
1. Clone the repository and navigate to the backend:
   ```bash
   git clone https://github.com/mrvanshgoel/NoteSphere.git
   cd NoteSphere/backend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Environment Configuration: Create a `.env` file in the `backend/` directory:
   ```env
   PORT=5000
   FIREBASE_SERVICE_ACCOUNT={"type": "service_account", "project_id": "..."}
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
4. Start the server:
   ```bash
   npm run dev
   ```

### 2. Android Setup
1. Open the `android/` directory in **Android Studio** (Jellyfish or newer).
2. Download your `google-services.json` from the Firebase Console and place it in `android/app/`.
3. Open `android/app/src/main/java/com/notesphere/app/api/ApiClient.java` and update the `BASE_URL` to point to your backend (e.g., `http://10.0.2.2:5000/` for localhost emulator).
4. Sync Gradle and click **Run**.

---

*NoteSphere represents the future of active learning—unifying fragmented academic tools into a single, cohesive, AI-native operating system.*
