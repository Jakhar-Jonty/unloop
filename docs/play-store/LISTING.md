# Unloop – Play Store listing and Console answers

Copy these into Play Console. Character limits are noted where Google enforces them.

## Main store listing

**App name** (30): `Unloop: Minimalist Launcher`

**Short description** (80):
`A calm, minimalist home screen with a mindful pause before distracting apps.`

**Full description** (4000):

```
Unloop is a calm, minimalist home screen for Android. It puts a gentle pause between you and the endless scroll, so you use your phone on purpose instead of on autopilot.

A QUIETER HOME SCREEN
• Just the apps you really need, as plain text. Everything else is one swipe away.
• A big clock, today's screen time and a random quote after every unlock.
• Search all your apps in a second, hide the ones you don't want to see.

A BREATHING PAUSE BEFORE DISTRACTING APPS
• Choose the apps that pull you in. Before they open, take one slow breath.
• "Why are you opening this?" – reply to someone, look something up, or just bored? If it's habit, Unloop suggests something better.
• The pause gets a little longer each time you open the same app in a day.
• Pause everywhere: also catches apps opened from notifications, Recents or links.

PROTECT YOUR PLANT
• Your plant starts every day with 7 leaves. Go over your daily limit and they start to fall.
• Keep every leaf for 7 days in a row and it flowers.

FOCUS AND LIMITS
• Focus mode: your home screen shrinks to the essentials, with optional Do Not Disturb.
• Schedules: Work, Wind down and Sleep turn Focus on by themselves.
• App time limits: after your time for an app is used up, it's blocked for the day.
• Strict mode: lock your settings for 1–7 days so you can only make them stricter.

TODAY: TO-DO AND NOTES
• Swipe left for your Today page: screen time, your plant, a to-do list and notes.

SEE YOUR WEEK
• Daily average, a 7-day chart, how often you turned back, and your top apps.
• Optional lock-screen wallpaper with today's screen time.

PRIVATE BY DESIGN
• No account. No ads. No analytics. Unloop has no internet access at all.
• Everything stays on your phone.

Available in English and Hindi.

ACCESSIBILITY SERVICE
Unloop uses Android's Accessibility API only for its "Pause everywhere" feature: to know which app is opened so it can show your pause or time-limit screen, to lock the screen when you double-tap home, and to open notifications when you swipe down. It never reads your screen content, messages or what you type, and nothing is stored or shared. The feature is optional and Unloop asks before you turn it on.
```

**Category:** Personalization  ·  **Tags:** Launcher, Productivity, Digital wellbeing
**Contact email:** jontysingh055@gmail.com
**Privacy policy URL:** `https://jakhar-jonty.github.io/unloop/privacy.html`

## Graphics (in this folder)

| Asset | File | Required size |
|---|---|---|
| App icon | `icon-512.png` | 512 × 512 PNG |
| Feature graphic | `feature-graphic-1024x500.png` | 1024 × 500 |
| Phone screenshots | `screenshots/*.png` | 2–8, 1080 × 2400 |

## App content (Policy → App content)

**Privacy policy:** the hosted URL.

**Ads:** No, the app does not contain ads.

**App access:** All functionality is available without special access (no login).

**Content rating** (questionnaire): Category "Utility, Productivity, Communication, or Other". Answer **No** to violence, sexuality, language, controlled substances, gambling, user-generated content sharing, location sharing, and purchases. Expected rating: Everyone / 3+.

**Target audience:** 18 and over (avoid selecting under-13 age groups).

**News app:** No. **Government app:** No. **Financial features:** None. **Health:** No.

**Data safety:**
- Does your app collect or share any of the required user data types? **No.**
  (Unloop has no INTERNET permission; everything is processed on-device and never transmitted. Per Google's definitions, on-device-only processing is not "collection".)
- Is all user data encrypted in transit? Not applicable (no data transmitted).
- Do you provide a way for users to request that their data be deleted? Data is deleted by uninstalling or clearing app storage.

**Accessibility API declaration** (Console asks when the app declares an AccessibilityService):
- Is it an accessibility tool (isAccessibilityTool)? **No.**
- Core functionality: digital wellbeing / screen-time management.
- Description to paste:

```
Unloop is a home screen launcher that helps users reduce compulsive phone use. Its optional "Pause everywhere" feature uses the AccessibilityService API to detect which app has come to the foreground (TYPE_WINDOW_STATE_CHANGED only; canRetrieveWindowContent is false). When the user opens an app they themselves marked as distracting, or one whose daily time limit they set has been reached, Unloop shows a breathing pause or time-limit screen on top, even when the app was opened from a notification, Recents or a link, which a launcher cannot otherwise see. The service is also used for two user-triggered global actions: locking the screen on a double-tap of the home screen, and opening the notification shade on a swipe down. No screen content is read, no data is stored, logged or transmitted (the app has no internet permission). Before the user is sent to Accessibility settings, a prominent in-app disclosure explains this and asks for consent.
```

- Video: record a short screen video (see README) and upload it to YouTube as unlisted, then paste the link.

## Release notes (1.0.0)

```
First release of Unloop: a calm, minimalist home screen with a breathing pause before distracting apps, a plant to protect, focus mode and schedules, app time limits, a Today page with to-do and notes, and a weekly reflection. Private by design: no account, no ads, no internet.
```
