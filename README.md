# Shot → Telegram (Android)

An Android app that captures the screen on a fixed interval and sends each
screenshot to a Telegram chat via your own bot. No local build tools required —
the APK is compiled in the cloud by GitHub Actions.

## How screen capture works (important)

Without root, Android cannot screenshot silently. This app uses the
**MediaProjection** API:

- The first time you tap **Start**, Android shows a system consent dialog.
- While capturing, Android shows a persistent "casting/recording" indicator in
  the status bar and an ongoing notification. **This cannot be hidden** — it is a
  platform privacy safeguard (stricter on Android 14+).
- Keeping the capture running in a foreground service avoids re-prompting on
  every shot, but the indicator stays visible the whole time.

The app only captures the screen of the device it is installed on, only after
you grant permission, and only sends to the Telegram chat you configure.

## 1. Create your Telegram bot

1. In Telegram, open **@BotFather** → `/newbot` → follow prompts.
2. Copy the **bot token** (looks like `123456:ABC-DEF…`).
3. Get your **chat id**: message **@userinfobot**, or send any message to your
   bot then open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser
   and read `chat.id`. (For a group, add the bot to the group first.)
4. Send your bot at least one message (`/start`) so it is allowed to message you.

## 2. Build the APK (cloud)

1. Create a new GitHub repository (empty, no README).
2. Push this folder to it (see commands printed by the assistant, or below).
3. GitHub → **Actions** tab → wait for **Build APK** to finish (~3–5 min).
4. Open the finished run → **Artifacts** → download **app-debug-apk** → unzip to
   get `app-debug.apk`.

Push commands:

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin main
```

## 3. Install on your phone

With your phone connected and USB debugging on (you already have `adb`):

```bash
adb install -r app-debug.apk
```

Or copy the APK to the phone and tap it (allow "install from unknown sources").

## 4. Use it

1. Open the app, paste your **bot token** and **chat id**.
2. Set the **interval** (seconds) and **JPEG quality**.
3. Tap **Save**, then **Start**, and approve the capture prompt.
4. Screenshots arrive in your Telegram chat on the interval. Tap **Stop** to end.

## Notes & limits

- `minSdk 29` (Android 10) and up.
- Battery optimizers on some phones may kill the service; exclude the app from
  battery optimization if captures stop.
- Debug APK is unsigned for release/Play Store — fine for personal sideloading.
