<div align="center">
  <h1>🎓 FeesManager</h1>
  <p><strong>Empowering Small Academies & Tutors to Become Smart, Digital, and Efficient</strong></p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-1.9.0-blueviolet?style=for-the-badge&logo=kotlin" alt="Kotlin Badge"/>
    <img src="https://img.shields.io/badge/Android-Native-green?style=for-the-badge&logo=android" alt="Android Badge"/>
    <img src="https://img.shields.io/badge/Supabase-Backend-3ECF8E?style=for-the-badge&logo=supabase" alt="Supabase Badge"/>
    <img src="https://img.shields.io/badge/Payments-Cashfree%20%7C%20Razorpay-blue?style=for-the-badge" alt="Payments"/>
  </p>
</div>

---

## 📖 Our Mission

The main motive of **FeesManager** is to help small academies, individual tutors, and tuition centers take a giant leap into the digital age with just a small step. Managing a tuition shouldn't be about drowning in paperwork, tracking down fee defaulters, or manually writing receipts. 

FeesManager digitizes your entire academy, bringing your students, teachers, and payments into one intuitive Android app.

---

## ✨ Core Features

- 👨‍🏫 **Smart Tuition Management:** Teachers can easily create classes, set monthly fees, and manually add students under their tuition. Students can also send join requests to be approved by the teacher.
- 💰 **Automated Fee Tracking:** Automatically track who has paid, who is pending, and spot fee defaulters instantly. No more manual ledgers!
- 🧾 **Digital Receipts & Payments:** Collect fees securely (via Cashfree or Razorpay) or record manual cash payments. Generate beautiful digital receipts and share them directly with students/parents via WhatsApp or email.
- 🤖 **AI-Powered Assistant:** A built-in AI teacher assistant (powered by Groq/Gemini) that analyzes your academy's data to generate insights, track student trends, and offer actionable advice.
- 💬 **Real-time Chat Hub:** Dedicated in-app messaging for classes and direct student queries, complete with attachment support so teachers can easily share notes and assignments.
- 📊 **Dynamic Dashboards:** Dedicated dashboards for Teachers and Students. Track pending fees, attendance, and revenue analytics at a glance.

---

## 🛠 Tech Stack

### Frontend (Android)
- **Language:** Kotlin
- **Architecture:** Clean Architecture (UI Layer, Data Layer, Domain Logic, Network Layer)
- **UI Toolkit:** Android Views, Material Design Components
- **Asynchronous Programming:** Kotlin Coroutines & Flows
- **Image Loading:** Glide

### Backend & Infrastructure
- **BaaS:** Supabase (PostgreSQL, Auth, Storage, Edge Functions)
- **Payment Gateways:** Cashfree SDK, Razorpay Checkout
- **AI Integration:** Groq REST API, Google Gemini API

---

## 📂 Project Architecture

This application strictly follows a modularized folder structure for clean, scalable, and maintainable code:

```text
com.example.feesmanager/
├── ai/          # AI logic, models, network clients (Groq/Gemini), and AI UI
├── base/        # Base classes (BaseActivity, etc.)
├── data/        # Data classes, Supabase network layer, Repositories
├── ui/          # UI layer containing specific features:
│   ├── auth/         # Login, Signup, Biometrics
│   ├── dashboard/    # Teacher and Student dashboards
│   ├── chat/         # Real-time class/student chats
│   ├── fees/         # Payment collection, Receipts, History, Calendar
│   ├── student/      # Manually adding students, join requests, profiles
│   └── ...           # settings, analytics, etc.
└── utils/       # Global utilities, themes, helpers (Glide, Notifications)
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Koala or newer recommended)
- **JDK 17** or above
- Supabase Project URL & Anon Key
- Cashfree / Razorpay API credentials

### Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mohit-agnihotri/FeesManager.git
   ```

2. **Configure Secrets:**
   Create a `local.properties` file in the root directory and add your API keys:
   ```properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_KEY=your-anon-key
   GROQ_API_KEY=your-groq-api-key
   ```

3. **Build & Run:**
   Sync the Gradle project in Android Studio, connect your emulator or physical device, and press Run!

---

<div align="center">
  <i>Developed with ❤️ to make small academies smarter.</i>
</div>
