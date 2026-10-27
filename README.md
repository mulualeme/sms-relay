# SMS Relay

[![Build APK](https://github.com/mulualeme/sms-relay/actions/workflows/build.yml/badge.svg)](https://github.com/mulualeme/sms-relay/actions/workflows/build.yml)

SMS Relay is an Android app that automatically forwards the SMS messages, app notifications and call alerts your phone receives to another phone number, an email address, a Telegram chat or any web URL (webhooks, Slack, Microsoft Teams, your own API). You decide what gets forwarded with rules: filter by sender, by message content, by app, and by day and time.

Everything runs on the phone. There is no SMS Relay server: messages go straight from your phone to the destinations you configure, using your own email account and your own Telegram bot.

> Its feature set and screens are modelled on the Forward SMS app. SMS Relay is an independent, clean-room reimplementation. It is not affiliated with Forward SMS or its developer and uses none of their code or servers.

## Download

- **Releases (recommended):** download `sms-relay-vX.Y.apk` from the [Releases page](https://github.com/mulualeme/sms-relay/releases). It is a signed release build, and later releases install over it as updates.
- **Latest commit:** open the [Actions tab](https://github.com/mulualeme/sms-relay/actions/workflows/build.yml), pick the newest successful run and download the `sms-relay-release-apk` (or `sms-relay-debug-apk`) artifact.

Because the APK comes from outside the Play Store, Android asks you to allow installing from unknown sources. Play Protect or your phone's app scanner may also warn about it, because an app that reads and forwards SMS looks like that to an automatic scanner. Read the code first if you are unsure.

## How it works

1. **Add a rule.** Tap **Add Rule** and pick a rule type.
2. **Choose where to forward.** A phone number, an email address, a Telegram chat id or a URL.
3. **Grant permissions.** SMS (and Contacts) for SMS rules, Notification Access for notification rules, Phone and Call log for call alerts.
4. **Test it.** After saving, the **Test Rule** dialog sends a sample message through the rule so you can check it arrives.

From then on, every matching message is forwarded in the background and logged in **History** with its result.

## Rule types

| Rule | What it does |
|---|---|
| **Basic Rule** | One destination, with an optional content filter and sender filter. The quickest setup. |
| **Advanced Rule** | A 5-step wizard: several destinations, custom message and email-subject templates, multiple sender filters, content condition groups, and settings for RCS, auto reply, retry, Wi-Fi only, SIM and schedule. |
| **Notification Rule** | Forwards notifications from other apps: all apps or only those you pick, with filters on notification title and text. |
| **Auto Reply Rule** | Replies to incoming SMS or calls (incoming call end, outgoing call end, missed call) automatically, under conditions you choose. |

### Filters

- **Content filters** use *Contains*, *Starts with*, *Ends with*, *Exactly matches with*, *Does not contains* or *Regex*.
  - Conditions are grouped. Inside a group you require **Any** or **All** conditions to match.
  - A message is forwarded if **any group** matches.
- **Sender filters** match the sender's phone number (formatting ignored) or contact name. If you add several, any one matching is enough.
- **Schedule** limits forwarding to chosen days of the week and, optionally, a time range.
- **SIM** (dual-SIM phones) limits a rule to messages received on one SIM, and chooses which SIM sends forwarded SMS.

### Message templates

Advanced and notification rules build the forwarded text from chips: **Sender**, **Message**, **Time**, **Rule Name**, **App Name**, **Sim Detail**, **New Line**, plus any text you type. A live preview shows the result. The default message is:

```
From: +15550100 (Jane Doe)
Your code is 123456
Received At: 10/26/2026, 4:13 PM
```

## Destinations

| Destination | Setup |
|---|---|
| **Phone number** | Sent as a regular SMS from your phone. Your carrier's SMS charges apply. |
| **Email** | **Settings → Email Configuration**. Choose **Gmail** (your address plus a [Google App Password](https://myaccount.google.com/apppasswords)) or **SMTP** (host, port, username, password, SSL or TLS). Use **Send Test Email** to check it. |
| **Telegram** | Create a bot with [@BotFather](https://t.me/BotFather) and paste its token in **Settings → Telegram**. Send your bot any message, then open `https://api.telegram.org/bot<token>/getUpdates` and use the `chat.id` you see there as the receiver's **Telegram Id**. |
| **URL** | Method (POST, PUT or GET), URL, headers and body. **Templates** fills in a body for a generic webhook, Slack or Microsoft Teams. |

### URL properties

These placeholders are replaced in the URL and body. In a JSON body the values are escaped automatically; in the URL they are URL-encoded.

| Property | Value |
|---|---|
| `{msg}` | Message text |
| `{sender}` | Sender number, with the contact name if known |
| `{time}` | UTC time in ISO 8601 format |
| `{local-time}` | Local time, e.g. `10/26/2026, 4:13 PM` |
| `{sim-detail}` | SIM carrier and number |
| `{rule-name}` | Name of the rule |
| `{app-name}` | App name (for notifications: the app that posted it) |
| `{formatted-msg}` | The full message built from the rule's template |

Example generic webhook body:

```json
{
  "message": "From: {sender}\n{msg}"
}
```

## More features

- **Phone Alerts.** Forward missed, incoming and outgoing calls (with duration), low battery, phone shut down and phone turned on to one destination.
- **Auto Reply.** Available as its own rule type or inside an advanced rule.
  - **Placeholders:** `{msg}`, `{sender_full_name}`, `{sender_first_name}`, `{sender_last_name}`, `{time_12}`, `{time_24}`, `{date}`, `{day_of_week}`, `{month}`, `{year}`. Add an offset for a later date or time: `{date+1}` is tomorrow and `{time_12 + 1}` is one hour later.
  - **Conditions:** phone is silent, Do Not Disturb is on, screen is locked or off, or phone is charging.
  - **Repeat replies to the same sender:** reply to all, once a day, stop after N replies, or pause for a set time.
  - **Timing:** a reply delay in seconds, and a "work when / do not work when" schedule.
- **Remote Reply.** Send an SMS *from* this phone by texting it from an allowed number:
  ```
  @5550100 I'll call you back
  @(Jane Doe) On my way
  ```
  Write `@`, then the phone number or saved contact name to send to, then the message. Wrap contact names that contain spaces in parentheses.
- **RCS.** Chat messages that only show up as notifications from your messaging app can be forwarded too. This needs Notification Access.
- **Offline retry.** Forwards that fail because there is no internet are retried automatically when the connection returns, for up to five attempts.
- **Export / import rules.** In **Settings → Rules**, save all rules to a `.json` file and load them on another phone, or after a reinstall. An imported rule replaces the rule with the same ID if it already exists, and is added otherwise, so importing the same file twice changes nothing. Exported files can contain URL headers such as API keys, so keep them private.
- **History.** Every forward is logged with its result per destination. You can view the exact message sent and any error. History is kept for a configurable number of days.
- **Stay running.** A sticky notification and the battery-optimization setting keep forwarding alive in the background, and rules start working again after a reboot.

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_SMS`, `READ_SMS` | Read incoming SMS to forward them |
| `SEND_SMS` | Forward to a phone number, auto reply, remote reply |
| `READ_CONTACTS` | Show and filter by the sender's contact name |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | Detect calls, list SIMs for SIM selection |
| `READ_CALL_LOG` | Call alerts and call-based auto replies |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Email, Telegram and URL forwarding; retry when back online |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*` | The sticky notification that keeps the app running |
| `RECEIVE_BOOT_COMPLETED` | Start again after the phone restarts |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask Android not to stop the app in the background |
| `QUERY_ALL_PACKAGES` | List installed apps when you pick apps for a notification rule |

Notification rules and RCS forwarding also need **Notification Access**, which you grant in Android settings.

## Privacy and security

- **Storage:** rules, settings and history stay in the app's private storage on your phone. Uninstalling the app or clearing its data deletes them.
- **Data flow:** messages only leave the phone for the destinations you set up. Nothing is sent to a server run by this project.
- **Passwords:** your SMTP or Gmail App Password is stored unencrypted in the app's private storage. Use an app-specific password, not your main password.
- **Cleartext HTTP:** plain `http://` URLs are allowed, so you can reach local or self-hosted endpoints. Use `https://` for anything that crosses the internet.
- **Scams:** forwarding SMS can expose bank one-time passwords. Never install or configure this app because someone else asked you to.

## Build from source

You need JDK 17 and the Android SDK (API 36).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
```

### Signed release builds

The release build is signed from four environment variables. Without them, `assembleRelease` produces an unsigned APK.

| Variable | Value |
|---|---|
| `SIGNING_STORE_FILE` | Path to the `.jks` keystore |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

```bash
./gradlew assembleRelease     # APK at app/build/outputs/apk/release/app-release.apk
```

### CI and releases

Every push to `main` and every pull request runs the tests and builds the debug APK on GitHub Actions. When the repository has the signing secrets (`SIGNING_KEYSTORE_BASE64`, the keystore file base64-encoded, plus `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`), it also builds the signed release APK.

Pushing a tag such as `v1.0` publishes a GitHub release with the signed APK attached:

```bash
git tag v1.0 && git push origin v1.0
```

Keep the keystore and its password backed up. Android only installs an update when it is signed with the same key, so if the key is lost, existing installs can't be updated.

### Project layout

| File | Contents |
|---|---|
| `Model.kt` | Rules, filters, schedule, template rendering, placeholders, remote-reply parsing |
| `Forwarder.kt` | Storage, sending (SMS, SMTP, Telegram, HTTP), auto reply, remote reply, offline retry |
| `Receivers.kt` | SMS, call, boot and shutdown receivers, the always-on service, the notification listener |
| `Ui.kt` | Theme and shared components (fields, dropdowns, dialogs, template chips, schedule) |
| `RuleScreens.kt` | Basic, Advanced, Notification and Auto Reply rule editors, Test Rule dialog |
| `Screens.kt` | Dashboard, menu, rules list, history, phone alerts, remote reply, settings, email, help |
| `MainActivity.kt` | Navigation, the save and permission flow, the first-run warning |

## Credits

The UI uses the [Montserrat](https://github.com/JulietaUla/Montserrat) font, licensed under the SIL Open Font License 1.1 (see [`FONT_LICENSE.txt`](FONT_LICENSE.txt)).
