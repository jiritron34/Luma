# Luma Finance

Luma is an offline-first personal finance Android app built with Kotlin, Jetpack Compose, Room, and MVVM. Money is stored as integer cents. A transfer is one ledger record with a source and destination account, so it changes each account balance without inflating income or spending.

## Build a debug APK

Run this from the project root (the bootstrap script downloads Gradle 8.9 to the Gradle user cache if needed):

```bash
sh ./gradlew testDebugUnitTest assembleDebug
```

On Windows use `gradlew.bat testDebugUnitTest assembleDebug`. A local build also needs JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0. The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow at `.github/workflows/android.yml` checks out the project, installs JDK 17, Gradle 8.9, and Android SDK Platform 35 / Build Tools 35.0.0, runs the financial unit tests, builds the debug APK, and uploads it as the `luma-finance-debug` workflow artifact. Push this project to GitHub, open **Actions**, select the latest successful **Android APK** run, and download the artifact.

## Create a signed release APK

Create a private Android keystore and keep it outside source control:

```powershell
keytool -genkeypair -v -keystore "$env:USERPROFILE\luma-release.jks" -alias luma -keyalg RSA -keysize 2048 -validity 10000
$env:LUMA_KEYSTORE_PATH = "$env:USERPROFILE\luma-release.jks"
$env:LUMA_KEYSTORE_PASSWORD = "your-keystore-password"
$env:LUMA_KEY_ALIAS = "luma"
$env:LUMA_KEY_PASSWORD = "your-key-password"
.\gradlew.bat assembleRelease
```

The signed APK is created at `app/build/outputs/apk/release/app-release.apk`. The build reads these secrets from environment variables; never commit them. Protect the keystore and passwords because losing the signing key prevents updates under the same app identity.

## Features

- Room-backed accounts, transactions, goals, and project budgets.
- Income, expense, and account-to-account transfer entries; transaction editing and deletion.
- Dashboard balance, selected-month totals and cash flow, transaction search and filters, annual summaries, category spending, goal progress, and project budget usage.
- CSV transaction export and versioned JSON backup / restore through Android's document picker.
- English and Spanish app copy, light and dark appearance, and four native home-screen widgets: balance, monthly spending, savings goals, and quick expense entry.
- Financial engine tests for account transfers, month/year boundaries, category totals, goal progress, and small expense multiplication.

## Data and privacy

The local Room database is the source of truth. There is no account sign-in or network permission. JSON restore replaces the local accounts, projects, goals, and transaction history with the selected backup.
