<div align="center">
  <h1>🎓 FeesManager</h1>
  <p><strong>A Modern, AI-Powered Academy & Fee Management Android Application</strong></p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-1.9.0-blueviolet?style=for-the-badge&logo=kotlin" alt="Kotlin Badge"/>
    <img src="https://img.shields.io/badge/Android-Native-green?style=for-the-badge&logo=android" alt="Android Badge"/>
    <img src="https://img.shields.io/badge/Supabase-Backend-3ECF8E?style=for-the-badge&logo=supabase" alt="Supabase Badge"/>
    <img src="https://img.shields.io/badge/Payments-Cashfree%20%7C%20Razorpay-blue?style=for-the-badge" alt="Payments"/>
  </p>
</div>

---

## 📖 Overview

**FeesManager** is a comprehensive solution tailored for academies, tutors, and educational institutions to manage their day-to-day operations seamlessly. From real-time chat and AI-driven teacher insights to secure, automated fee collection via Cashfree/Razorpay, FeesManager centralizes everything in one intuitive native Android app.

---

## ✨ Key Features

- 🤖 **AI-Powered Assistant:** Built-in AI teacher assistant (powered by Groq/Gemini LLMs) to generate insights, track student trends, and offer actionable advice based on class analytics.
- 💳 **Seamless Payments:** Integrated with Cashfree and Razorpay for seamless fee collection, advance payments, receipt generation, and monthly rollovers.
- 💬 **Real-time Chat Hub:** In-app messaging for classes and direct student queries, complete with attachment support.
- 📊 **Dynamic Dashboards:** Dedicated dashboards for Teachers and Students. Track pending fees, defaulters, attendance, and revenue analytics at a glance.
- 🔔 **Smart Notifications:** Real-time push notifications for fee reminders, new chat messages, announcements, and join requests.
- 🔐 **Secure & Scalable:** Powered by Supabase. Role-based authentication (Teacher/Student), secure preferences, and biometric login support.

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
│   ├── fees/         # Payment collection, History, Calendar
│   ├── student/      # Student profiles, join requests, approval flows
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

## 🤝 Contribution

Feel free to open issues or submit Pull Requests for any improvements, bug fixes, or new features.

<div align="center">
  <i>Developed with ❤️ for educators and students.</i>
</div>
