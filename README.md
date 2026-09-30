# Expense Tracker

An Android app (Kotlin, Jetpack Compose) that reads bank and UPI debit/credit SMS on your phone,
saves the amount, and asks you for a category and comment. Everything stays on the phone.

## What it does

- Reads incoming bank / UPI SMS automatically and saves the amount, merchant, bank, account and date.
- Shows a notification with four category buttons and an "Add comment" reply box. Tap it to open the full editor.
- Today is the first tab and shows only today's spends with a ring for the monthly budget. Week, Month and Year
  have a total, change vs the previous period, charts, category breakdown and day-by-day (or month-by-month) lists.
- Dark mode is true black for AMOLED screens, with a colourful gradient. Light mode follows the phone setting.
- Make your own categories with an emoji or a picture from your gallery.
- Imports your old SMS once (Settings tab), then you tag them from the "Needs category" list.
- Remembers the category you pick for a merchant and applies it to other untagged payments there.
- Credit card bill payments and transfers are kept out of the spending and income totals.
- Manual entry for cash expenses. CSV export (save it to Google Drive from the file picker).

## Install (sideload)

1. Open the latest run of the **Build** workflow under the Actions tab and download `expense-tracker-debug-apk`.
2. Unzip it and copy `app-debug.apk` to the phone, then open it to install.
3. On Android 13+: Settings > Apps > Expense Tracker > ⋮ > **Allow restricted settings**, then grant the SMS permission.

## Layout

- `core/` pure Kotlin: SMS parser, categorizer and report maths. Has unit tests: `CORE_ONLY=1 ./gradlew :core:test`
- `app/` the Android app (Jetpack Compose, plain SQLite).
