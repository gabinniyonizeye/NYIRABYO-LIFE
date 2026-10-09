# NYIRABYO LIFE 🛡️✝️

> **"Live with purpose."**  
> An offline-first, faith-anchored Android launcher and digital discipline guardian designed to protect spiritual focus, master daily priorities, and break mindless social media addiction.

---

## 🌟 Overview

**NYIRABYO LIFE** replaces chaotic, dopamine-driven home screens with a calm, intentional command center. Rooted in discipline, faith, and daily progress, it provides real-time application monitoring, intentional usage budgets, and a private recovery sanctuary for living an undivided life.

---

## 🚀 Key Features

### 1. 📱 Horizontal Multi-Slide Launcher (`ViewPager2`)
Instead of an endless vertical scroll, the home experience is divided into three focused, fluid horizontal slides:

- **Slide 1 — Daily Walk (Faith & Priorities)**:
  - High-contrast digital clock and date display.
  - **Today's Word**: Curated daily scripture verse with reflection prompts.
  - **Today's Mission**: Track and complete your top 3 daily priorities (e.g., Prayer, Coding, Training) with real-time progress tracking.

- **Slide 2 — Tools & Command Center**:
  - **God First**: Verse reflection and prayer intention journal.
  - **Focus Timer**: Deep focus sessions for study and craft.
  - **Discipline Sanctum**: Private recovery area protected by Biometric / PIN authentication.
  - **Social Limits Quick Card**: Direct shortcut to digital entertainment budgets.
  - **Strict 1-Hour Escape Room Block**: Instant 60-minute lockdown (only incoming phone calls permitted) to shatter acute impulses.
  - **WhatsApp Daytime Banner**: Quick glance at active communication hours with a one-tap launcher.

- **Slide 3 — Digital Boundaries & App Budgets**:
  - **Instagram (30-Min Daily Budget)**: Live progress bar showing minutes spent today vs. remaining allowance.
  - **TikTok (30-Min Daily Budget)**: Live progress bar and intentional launch gate.
  - **WhatsApp Schedule (08:00 – 20:00)**: Relaxed daytime access window with mindful pause outside hours.
  - **App Restrictions Hub**: Manage individual timers and settings protection across all installed apps.

---

### 2. 🛡️ Guardian Discipline Engine

- **WhatsApp Daytime Window (08:00 – 20:00)**:
  - Usable throughout the day from **8:00 AM to 8:00 PM** for healthy communication.
  - Outside this window (late night and early morning), a mindful cooling-off pause protects rest and sleep quality.

- **Instagram & TikTok 30-Minute Cap**:
  - Rather than total blunt blocking, users are given a **30-minute daily budget** for high-distraction entertainment apps.
  - While under 30 minutes, apps launch freely. Once 30 minutes have elapsed, the **Block Shield** activates immediately with mindful pause delays and biblical wisdom (*1 Corinthians 6:12*).

- **Settings Guard & Anti-Tamper**:
  - Prevents impulsive disabling of accessibility services or force-stopping protections through Android Settings.
  - Requires your secret Recovery PIN to unlock settings for 2 minutes.

- **Background Process Terminator**:
  - Automatically kills non-essential background processes when high-distraction apps are minimized, preventing passive background consumption.

---

### 3. 🔒 100% Offline & Private Sanctuary

- **Zero Cloud Telemetry**: All data stays exclusively on your physical device.
- **Local SQLite Database**: Fast, reliable offline storage for missions, prayers, gratitude logs, and victory records.
- **Biometric & PIN Security**: Sensitive recovery records and urge check-ins are locked behind cryptographic SHA-256 PIN authentication.
- **JSON Data Backup**: Full offline JSON export and wipe controls at any time.

---

## 🛠️ Tech Stack

- **Platform**: Android (Kotlin)
- **Minimum SDK**: API 24 (Android 7.0 Nougat)
- **Target SDK**: API 36 (Android 16 / Vanilla Ice Cream)
- **Architecture**: Clean Architecture with Local SQLite & SharedPreferences
- **UI & Navigation**: Jetpack ViewPager2, ViewBinding, Material Components
- **System Services**:
  - Android Accessibility Service (`NyirabyoGuardianService`) for real-time window tracking
  - `UsageStatsManager` for system-level foreground usage accuracy
  - Android `BiometricPrompt` API for biometric unlock

---

## 📦 Building and Running

### Prerequisites
- JDK 21
- Android SDK with platform tools (API 36)

### Build Debug APK
```bash
# Clone the repository
git clone https://github.com/gabinniyonizeye/NYIRABYO-LIFE.git
cd NYIRABYO-LIFE

# Assemble debug build
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

---

## ⚙️ Initial Setup & Permissions

To enable the full Guardian protection:
1. **Set as Default Launcher**: Go to Android Settings > Apps > Default Apps > Home app > select **NYIRABYO LIFE**.
2. **Accessibility Service**: Enable **NYIRABYO Guardian** in Accessibility settings to allow real-time app window detection.
3. **Usage Access**: Grant Usage Access to allow accurate daily app time budgeting.
4. **Draw Over Other Apps**: Allow overlay permissions so the Block Shield can intercept restricted apps.

---

## 📜 License

Created with purpose for personal growth, spiritual discipline, and digital freedom.
