# Changelog

## v1.5

### Fixed
- **The app no longer closes when many messages arrive at once.** Each incoming SMS was held open until its forward finished. Forwards run one at a time and can take up to 35 seconds on a slow network, so queued messages ran past Android's time limit and Android closed the app. Each SMS is now released within 8 seconds, and the forward continues in the background.
- **Less load on the screen thread during bursts of messages.** The MMS watcher was triggered by every SMS and checked the message database on the screen thread. It now runs in the background.
- **History opens quickly with many entries.** Only the entries on screen are drawn, instead of all 1,000.

## v1.4

### Fixed
- **Rule editor screens, Rules List and Edit URL Config match the original's size.** Template chips, buttons, text fields, dropdown boxes, step titles, help icons and day circles were 10–20% larger than the original.
- **The "Previous" button no longer wraps onto two lines** ("Previou / s").
- **Rule menu (Edit / Test / Pause / Delete)** uses the original's text size and spacing.

## v1.3

### Fixed
- **Popups and dropdowns now use the same size as the rest of the app.** They open in their own window, which reset them to full size, so they looked about 15% larger than the screens behind them.
- **Overall size restored to 0.88×.** v1.1 and v1.2 shipped at 0.8× by mistake, which made everything slightly too small.
- **Dialog backgrounds match the original.** Help, confirm and test dialogs are near-white; the History "Message Sent" / error popups stay grey.

## v1.2

### New
- **Export and import rules.** In **Settings → Rules**, save all your rules to a `.json` file and load them on another phone, or after a reinstall.
  - An imported rule replaces the rule with the same ID if it already exists, and is added otherwise, so importing the same file twice changes nothing.
  - Exported files can contain URL headers such as API keys, so keep them private.

## v1.1

### Fixed
- **Sender filters match a phone number however it is written.** A filter of `0945287216` now matches messages from `+251945287216` or `+251 94 528 7216`.
- **"Does not contains" checks both the number and the contact name.** Before, a saved contact could slip past it.
- **Test Rule applies the rule's filters, SIM and schedule.** If the test message wouldn't be forwarded, the test shows why (for example "Sender does not contain CBE") instead of sending it anyway.

### New
- **MMS forwarding.** Picture messages, and long texts some carriers deliver as MMS, are forwarded like SMS. This needs the sticky notification to be on, which it is by default.

## v1.0

First release.

- **Rule types:**
  - Basic Rule.
  - Advanced Rule: multiple receivers, message and subject templates, sender filters, content condition groups, schedule, SIM selection, Wi-Fi only and offline retry.
  - Notification Rule.
  - Auto Reply Rule.
- **Destinations:** phone number (SMS), email (Gmail App Password or SMTP), Telegram (your own bot), or any URL / webhook, with templates for Slack and Microsoft Teams.
- **Phone Alerts:** missed, incoming and outgoing calls, low battery, shut down and turned on.
- **Remote Reply:** send an SMS from this phone by texting it `@number message`.
- **History and statistics:** every forward is logged with its result.
