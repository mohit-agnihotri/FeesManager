# FeesManager — Codebase Architecture Guide

## Overview
This project is an Android fees management app built in Kotlin.
It serves two user roles: **Teacher** and **Student**.
The backend is **Supabase** (PostgreSQL + Auth + Realtime + Edge Functions).
Payments are handled via **Cashfree Easy Split** and **Razorpay**.

---

## Intended Package Structure (Refactor Target)
> Note: Files are currently flat in the root package.
> The structure below is the **intended** target for future refactoring via Android Studio → Refactor → Move.

```
com.example.feesmanager/
│
├── auth/                          ← App entry, login, role selection
│   ├── SplashActivity.kt
│   ├── LanguageSelectActivity.kt
│   ├── RoleSelectActivity.kt
│   ├── MainActivity.kt
│   ├── SessionManager.kt
│   └── LocaleHelper.kt
│
├── teacher/                       ← Teacher-only screens & features
│   ├── dashboard/
│   │   ├── DashboardActivity.kt
│   │   ├── MultiAcademyActivity.kt
│   │   └── AnalyticsActivity.kt
│   ├── students/
│   │   ├── AddStudentActivity.kt
│   │   ├── EditStudentActivity.kt
│   │   ├── ViewStudentsActivity.kt
│   │   ├── PendingStudentsActivity.kt
│   │   ├── AdvanceStudentsActivity.kt
│   │   └── StudentProfileActivity.kt
│   ├── fees/
│   │   ├── FeesEntryActivity.kt
│   │   ├── SetClassFeesActivity.kt
│   │   ├── FeeCalendarActivity.kt
│   │   ├── HistoryActivity.kt
│   │   ├── AdvancePaymentActivity.kt
│   │   ├── PaymentRequestsActivity.kt
│   │   └── ReceiptActivity.kt
│   ├── attendance/
│   │   └── AttendanceActivity.kt
│   ├── announcements/
│   │   └── AnnouncementsActivity.kt
│   ├── chat/
│   │   ├── ClassChatActivity.kt
│   │   ├── ClassSelectChatActivity.kt
│   │   └── StudentQueriesActivity.kt
│   ├── payment/                   ← Payment gateway onboarding
│   │   ├── CashfreeOnboardingActivity.kt
│   │   ├── RazorpayOnboardingActivity.kt
│   │   └── AppPaymentConfig.kt
│   ├── settings/
│   │   ├── SetupProfileActivity.kt
│   │   ├── SettingsActivity.kt
│   │   └── BackupActivity.kt
│   └── TeacherAiActivity.kt
│
├── student/                       ← Student-only screens & features
│   ├── auth/
│   │   ├── StudentLoginActivity.kt
│   │   ├── StudentSignupActivity.kt
│   │   ├── StudentJoinActivity.kt
│   │   └── StudentPendingApprovalActivity.kt
│   ├── dashboard/
│   │   ├── StudentDashboardActivity.kt
│   │   └── StudentFaqActivity.kt
│   ├── fees/
│   │   └── PayFeesActivity.kt
│   └── chat/
│       └── MessageActivity.kt
│
├── common/                        ← Shared utilities — used by BOTH roles
│   ├── BaseActivity.kt            ← All activities extend this
│   ├── ThemeManager.kt
│   ├── AnimUtil.kt
│   ├── InputValidator.kt
│   ├── SecurePrefs.kt
│   ├── NotificationHelper.kt
│   ├── WhatsAppHelper.kt
│   ├── GlideHelper.kt
│   ├── BiometricHelper.kt
│   ├── DrawerHelper.kt
│   └── UnreadBadgeHelper.kt
│
├── ai/                            ← AI assistant (already well-organized)
│   ├── GeminiClient.kt
│   ├── GroqClient.kt
│   ├── PromptTemplates.kt
│   ├── AiChatMessage.kt
│   ├── actions/
│   ├── adapter/
│   ├── student/
│   └── teacher/
│
└── data/                          ← Data layer (already well-organized)
    ├── FmResult.kt                ← Sealed class: Loading / Success / Error
    ├── SupabaseManager.kt         ← Supabase client singleton
    ├── model/                     ← Data classes / POJOs
    └── repository/                ← All Supabase API calls live here

```

---

## Redundancy Notes (Future Cleanup)

| Files | Issue | Action |
|---|---|---|
| `MessageActivity` + `ClassChatActivity` | Both are chat screens, ~80% shared code | TODO: Merge into one ChatActivity with an `isGroupChat: Boolean` flag |
| `StudentQueriesActivity` + `ClassSelectChatActivity` | Both are "select a conversation" list screens | TODO: Merge into one with mode flag |
| `CashfreeOnboardingActivity` + `RazorpayOnboardingActivity` | Shared form validation and bank account UI | TODO: Extract shared form fields to a common layout |
| `DashboardActivity` (12.9 KB) | Too large — doing too much | TODO: Extract stats loading to DashboardViewModel |
| `DrawerHelper` (9.6 KB) | Contains navigation AND language switching | TODO: Extract language picker to LocaleHelper |

---

## Data Flow

```
Activity / Fragment
      ↓ calls
  ViewModel  (ui/*)
      ↓ calls
  Repository (data/repository/*)
      ↓ calls
  Supabase   (via SupabaseManager)
      ↓ returns
  FmResult<T>  (Loading | Success | Error)
      ↓ observed by
  Activity via LiveData
```

---

## Key Conventions
- All activities extend `BaseActivity` for automatic locale + theme handling
- All Supabase calls return `FmResult<T>` — never throw raw exceptions to the UI
- `SessionManager` is the single source of truth for who is logged in
- `SecurePrefs` wraps EncryptedSharedPreferences for storing sensitive keys
- `DrawerHelper` sets up the side navigation drawer for both teacher and student roles
