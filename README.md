<p align="center">
  <img src="docs/icon.png" width="96" alt="">
</p>

<h1 align="center">OTP Relay</h1>

<p align="center">
  <sub><i>For when someone you trust needs an OTP and you would rather not stop what you are doing.</i></sub>
</p>

<p align="center">
  <b>Forward OTP texts to one person, for a set time.</b>
  <br>
  Pick a contact and a duration; any text with the word "OTP" in it is relayed over your own SIM
  until the time is up.
  <br>
  <br>
  No server, no SMS gateway, no network calls.
</p>

<p align="center">
  <a href="https://github.com/seeknull/otp-relay/releases/latest"><b>Download APK — v1.2</b></a>
  &nbsp;·&nbsp; Android 8.0+ &nbsp;·&nbsp; MIT
</p>

<p align="center">
  <img src="docs/screenshot-home.png" width="46%" align="top" alt="Home screen with quick actions">
  &nbsp;
  <img src="docs/screenshot-history.png" width="46%" align="top" alt="History grouped by session">
</p>

## How to use

Three ways to start. Each one stops by itself when the time is up.

**👤 1. Pick a contact**  
Tap **+ Contact**. Pick a name. Pick a time. Tap **Start**.

**⚡ 2. Save a quick action**  
Do the same, and tap **Save shortcut**. Next time it is one tap.

**🔗 3. Send a link**  
Tap **Share**. Send the link on WhatsApp. They keep it.
When they need a code, they send the link back. You tap it and approve.

It never starts by itself. You always tap. Codes only go to a name in your phone book.

## Features

- Sessions of 5, 15 or 30 minutes, or 1 hour.
- **Only saved contacts can receive OTPs.** A number has to be chosen from your phone book first,
  so a link from a stranger cannot point forwarding at an unknown number.
- **No notification, no forwarding.** A session will not start without a visible notification, and
  stops itself within 5 seconds if that notification is ever blocked or removed.
- Quick actions — save a person and a duration, start with one tap.
- A short beep when a message is relayed, so you know without looking.
- The recipient is texted when a session starts and when it ends.
- Every message sent is logged, grouped by session, with how long it took to go out.
- **Request links** — send someone a link; they send it back when they need an OTP. It opens the
  approval prompt with the details filled in. Nothing starts until you approve, and the number
  still has to be one of your contacts.

A forwarded text names who the code came from and is marked `(fwd by OTP Relay)`.

Sending is quick, because OTPs expire: the message reaches the modem before anything is written to
disk, typically in well under 50 ms.

## Privacy

No data is collected or shared. No analytics, no accounts, no cloud backup, no server.

The app does not hold the `INTERNET` permission, so it cannot reach the network even in principle —
that is checkable in the manifest rather than something you have to take on trust.

- **SMS is only read while a session is running.** Outside a session the app is not watching, and
  nothing read is ever uploaded, since the app makes no network calls at all.
- The inbox is never read — messages come from the incoming broadcast, so no `READ_SMS`. Anything
  without "OTP" is ignored and never stored.
- Messages that are forwarded are kept in the on-device log, so you can check what went out. That
  log lives in the app's private storage, is excluded from Android backup, and never leaves the
  phone. Clear app data and it is gone.
- Request links carry only a number, after the `#` — which browsers never send to a server, so it
  never reaches GitHub even if the recipient does not have the app. No name is ever put in a link,
  because that would be sender-controlled text sitting next to a phone number.

The usual caveat: whichever chat app you send a link through can see it, like any message.

## Permissions

| Permission | Why it is needed |
| --- | --- |
| `RECEIVE_SMS` | Read incoming texts during a session |
| `SEND_SMS` | Send the forwarded text and the start/stop notices |
| `POST_NOTIFICATIONS` | Show the "forwarding is on" notification |
| `FOREGROUND_SERVICE` | Keep listening for the length of the session |
| `FOREGROUND_SERVICE_SPECIAL_USE` | The category that fits an SMS relay |

All five are required. Not requested: `READ_SMS`, `READ_CONTACTS`, `READ_PHONE_NUMBERS`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Build your own APK

Needs the Android SDK (platform 36) and JDK 17 or newer.

```
git clone https://github.com/seeknull/otp-relay.git
cd otp-relay
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew test              # unit tests
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a **release** APK, create a keystore once and point the build at it:

```
keytool -genkeypair -v -keystore ~/otp-relay.keystore -alias otprelay \
  -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleRelease \
  -Potprelay.keystore=$HOME/otp-relay.keystore \
  -Potprelay.storePassword=... -Potprelay.keyAlias=otprelay -Potprelay.keyPassword=...
```

Keep that keystore. Request links verify against the signing certificate, so a different key breaks
them until you publish a matching `assetlinks.json`. Without a keystore the release build is left
unsigned.

Debug builds can load made-up contacts and history for screenshots. It lives in memory only, so
real data is untouched and returns on the next launch:

```
adb shell am start -n com.guru.otprelay/.MainActivity --ez demo true
```

## Google Play

**Unlikely to be accepted.** Play restricts SMS permissions to default SMS, Phone or Assistant
handlers. The one [exception](https://support.google.com/googleplay/android-developer/answer/10208820)
that might fit — device automation — is meant for general automation apps, needs a declaration form
and review, and auto-relaying passcodes resembles the fraud pattern reviewers screen for.

The core function is the problem, not the code. Sideloading has none of these limits.

## Notes

- A reboot ends any running session, and only one session runs at a time.
- "My number" is optional and typed once. It cannot be detected reliably, because most carriers do
  not write your number to the SIM.
- Request links are verified App Links, so they open the app directly rather than a browser.
  Verification is tied to both the host and the signing key: if you fork this, point `WEB_HOST` in
  `RequestLink.kt` and the `<intent-filter>` in the manifest at a host you control, then serve your
  own certificate fingerprint from `/.well-known/assetlinks.json` on it.
- Some phones stop background apps aggressively, which can cut a session short. Settings has a
  shortcut to Android's battery screen if that happens.

## Licence

MIT — see [LICENSE](LICENSE).
