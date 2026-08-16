# OTP Relay

An Android app that forwards incoming OTP texts to one person, for a set length of time.

You pick a number and a duration. While the session is running, any SMS whose body contains
"OTP" is relayed to that number over your own SIM. When the time is up it stops on its own.

There is no server and no SMS gateway. Everything — the destination, the history, the settings —
stays in the app's own storage on the phone. Clear the app's data and it is all gone.

## What it does

- Sessions of 5 min, 15 min, 30 min, 1 hour, 6 hours or 1 day.
- Destination typed by hand or picked from Contacts.
- Shortcuts: save a person and a duration, then start it with one tap next time.
- A persistent notification for as long as forwarding is on, with a Stop button. It disappears
  when the session ends.
- **No notification means no forwarding.** The session refuses to start if the notification cannot
  be shown, and stops itself within five seconds if the notification is ever turned off, blocked or
  removed while running. Failing to forward is treated as better than forwarding unseen.
- The recipient gets a text when a session starts and another when it ends, both naming your
  number if you have filled it in.
- Every message sent is logged with the sender, the destination, the time it arrived and how long
  it took to go out. Logs are grouped by session.
- Request links: generate a link for someone and send it to them. When they need an OTP they send
  it back; opening or pasting it shows an approval prompt with the number and duration filled in,
  both editable. Nothing starts until you approve.

Forwarded messages carry a `(fwd by OTP Relay)` marker so the recipient knows where they came from.

Speed matters, because OTPs expire. The message is handed to the modem before anything is written
to disk, and each log row shows the arrival-to-sent time.

## Privacy

No data is collected or shared. The app makes no network calls at all — no analytics, no accounts,
no cloud backup, no server.

- **SMS is only read while a session is running.** The receiver starts with the session and stops
  with it. Outside a session the app is not watching your messages.
- The SMS **inbox is never read** — messages come from the incoming broadcast, which is why
  `READ_SMS` is not requested. Anything without "OTP" is ignored and never stored.
- Destinations, shortcuts and history stay in the app's private storage and are excluded from
  Android backup. Clear app data and they are gone.
- The only thing that leaves the phone is the forwarded text, sent over your carrier to the one
  number you chose.

**Request links share nothing either.** The number sits after the `#` in the link, and browsers
never send that part to the server — so it never reaches GitHub, even if the recipient has no app
installed. The page it points at is static HTML with no scripts or cookies. A unit test fails if a
number ever ends up before the `#`.

The usual caveat applies: whichever chat app you send the link through can see it, like any message.

## Permissions

Only five, and all five are load bearing.

| Permission | Why |
| --- | --- |
| `RECEIVE_SMS` | Read incoming messages while a session is running |
| `SEND_SMS` | Send the forwarded message and the start/stop notices |
| `POST_NOTIFICATIONS` | Show the "forwarding is on" notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep listening for the length of the session |

Deliberately not requested:

- `READ_SMS` — incoming messages are read from the broadcast itself, never from the SMS inbox.
- `READ_CONTACTS` — the system contact picker returns the one number you chose and nothing else.
- `READ_PHONE_NUMBERS` — carriers rarely record your own number on the SIM, so it mostly returned
  nothing anyway. Type it in once instead; it is remembered.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — the app opens the system battery screen instead of
  asking to be exempted itself.

## Installing

Sideloading is the supported route. Needs the Android SDK (platform 36) and JDK 17 or newer.

```
git clone <this repo>
cd otprelay
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew test` runs the unit tests.

## About Google Play

Read this before planning a Play release.

Play puts SMS in its [restricted permissions](https://support.google.com/googleplay/android-developer/answer/10208820)
group. The general rule is that an app must be the device's default SMS, Phone or Assistant
handler before it may even prompt for `RECEIVE_SMS` or `SEND_SMS`. This app is none of those, and
becoming a full default SMS handler would mean building an entire messaging app.

There is a separate exceptions table that does not carry the default-handler requirement. The only
row that plausibly fits is **device automation** — "apps enabling users to automate repetitive
actions across multiple OS areas, based on user-defined conditions and triggers". OTP Relay is one
narrow trigger rather than a general automation tool, so the fit is arguable at best, and it would
need a Permissions Declaration Form and a manual review. Automatically relaying one-time passcodes
to another person is also exactly the shape of a fraud pattern reviewers look for.

So, honestly: **a Play release is unlikely to be approved**, and nothing about the code changes
that — the core function is the problem, not the implementation. The permission list above is
already at the minimum, which is what a review would want, but do not count on it passing.

Sideloading has none of these constraints and is what the install steps above describe.

## Notes

- Sessions do not survive a reboot. If the phone restarts mid-session, forwarding stops and you
  start a new one.
- Request links are verified Android App Links, so they are tappable in chat apps and open the
  app directly with no browser and no chooser. Verification pins the signing certificate, so
  **release builds must use the same keystore**; see below. The `otprelay://` scheme still works,
  and **Copy** / "Open a request link from the clipboard" remain as fallbacks.
- Verification only succeeds for builds signed with a certificate listed in
  [assetlinks.json](https://seeknull.github.io/.well-known/assetlinks.json). Both the release key
  and the project's debug key are listed. If you fork this, change `WEB_HOST` in `RequestLink.kt`
  and the manifest to a host you control, and publish your own `assetlinks.json` with your
  certificate's SHA-256.
- Some manufacturers kill background apps aggressively. For long sessions, use the battery
  settings button, and check your phone's settings for an autostart toggle.
- Only one session runs at a time. Starting another closes the first.

## Licence

MIT. See [LICENSE](LICENSE).
