<img src="docs/icon.png" width="88" align="left" alt="">

# OTP Relay

**Forward OTP texts to one person, for a set time.**
Everything happens on your phone — no server, no SMS gateway, no network calls.

<br clear="left">

[**Download the APK →**](https://github.com/seeknull/otp-relay/releases/latest) · Android 8.0+ · MIT

---

Pick a number and a duration. While the session runs, any text containing "OTP" is relayed to that
number over your own SIM. When the time is up, it stops on its own.

## Features

- Sessions of 5 min, 15 min, 30 min, 1 hour, 6 hours or 1 day.
- **Only saved contacts can receive OTPs.** A number has to be chosen from your phone book first,
  so a link from a stranger cannot point forwarding at an unknown number.
- A short beep when a message is relayed, so you know without looking.
- **No notification, no forwarding.** A session will not start without a visible notification, and
  stops itself within 5 seconds if that notification is ever blocked or removed.
- Shortcuts — save a person and a duration, start with one tap.
- The recipient is texted when a session starts and when it ends.
- Every message sent is logged, grouped by session, with how long it took to go out.
- **Request links** — send someone a link; they send it back when they need an OTP. It opens the
  approval prompt with the details filled in. Nothing starts until you approve, and the number
  still has to be one of your contacts.

Forwarded texts are marked `(fwd by OTP Relay)`. Sending is quick, because OTPs expire: the message
reaches the modem before anything is written to disk, typically in well under 50 ms.

## Privacy

No data is collected or shared. No analytics, no accounts, no cloud backup, no server.

- **SMS is only read while a session is running.** Outside a session the app is not watching.
- The inbox is never read — messages come from the incoming broadcast, so no `READ_SMS`. Anything
  without "OTP" is ignored and never stored.
- History stays in private storage and is excluded from Android backup. Clear app data, it is gone.
- Request links carry only a number, after the `#` — which browsers never send to a server, so it
  never reaches GitHub even if the recipient does not have the app. No name is ever put in a link,
  because that would be sender-controlled text sitting next to a phone number.

The usual caveat: whichever chat app you send a link through can see it, like any message.

## Permissions

Five, all required.

| | |
| --- | --- |
| `RECEIVE_SMS` | Read incoming texts during a session |
| `SEND_SMS` | Send the forwarded text and the start/stop notices |
| `POST_NOTIFICATIONS` | Show the "forwarding is on" notification |
| `FOREGROUND_SERVICE`, `..._SPECIAL_USE` | Keep listening for the length of the session |

Not requested: `READ_SMS`, `READ_CONTACTS`, `READ_PHONE_NUMBERS`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Build

```
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs the Android SDK (platform 36) and JDK 17+. `./gradlew test` runs the unit tests.

## Google Play

**Unlikely to be accepted.** Play restricts SMS permissions to default SMS, Phone or Assistant
handlers. The one [exception](https://support.google.com/googleplay/android-developer/answer/10208820)
that might fit — device automation — is meant for general automation apps, needs a declaration form
and review, and auto-relaying passcodes resembles the fraud pattern reviewers screen for.

The core function is the problem, not the code. Sideloading has none of these limits.

## Notes

- A reboot ends any running session, and only one session runs at a time.
- "My number" is optional and typed once. It cannot be detected reliably — most carriers do not
  write your number to the SIM.
- Request links are verified App Links, so they open the app directly. Verification pins the
  signing key, so release builds need the project keystore. Forking? Point `WEB_HOST` in
  `RequestLink.kt` and the manifest at a host you control, and publish your own
  [assetlinks.json](https://seeknull.github.io/.well-known/assetlinks.json).
- Some phones kill background apps aggressively. For 6 hour and 1 day sessions, use the in-app
  battery button.

## Licence

MIT — see [LICENSE](LICENSE).
