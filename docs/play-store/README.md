# Publishing Unloop on Google Play

Everything in this folder is ready to upload. `LISTING.md` has every piece of text to paste.

## 0. Back up the upload key (do this first)

The release is signed with an **upload key** stored outside the project:

- Key: `~/unloop-signing/upload-keystore.jks`
- Passwords: `keystore.properties` in the project root (git-ignored, never committed)

Copy both to a safe place (password manager, encrypted drive). With Play App Signing,
a lost upload key can be reset through Play support, but it takes days.

## 1. Host the privacy policy

Play needs a public URL. The policy is `docs/privacy.html`. The GitHub repo is private,
so pick one:

- **Google Sites** (free, 5 minutes): new site → paste the text of `privacy.html` → Publish → copy the URL.
- **A separate public GitHub repo** with only `privacy.html` (renamed `index.html`) and GitHub Pages turned on.
- **Netlify Drop**: drag the `docs` folder onto app.netlify.com/drop.

## 2. Create the app in Play Console

Play Console → **Create app** → name `Unloop: Minimalist Launcher`, default language English (United States),
App, Free. Accept the declarations.

## 3. Upload the build

Testing → **Internal testing** → Create release → upload `Unloop-1.0.0.aab`.
Accept **Play App Signing** when asked (recommended). Paste the release notes from `LISTING.md`.

New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before
production. Internal testing (up to 100 testers) works right away and is the fastest way to try the
Play-installed build yourself.

## 4. Store listing

Grow → Store presence → **Main store listing**: paste name, short and full description from `LISTING.md`,
then upload:

- App icon: `icon-512.png`
- Feature graphic: `feature-graphic-1024x500.png`
- Phone screenshots: everything in `screenshots/` (in order 1–8)

## 5. App content (Policy → App content)

Answer each section using `LISTING.md`: privacy policy, ads (no), app access, content rating,
target audience (18+), data safety (no data collected or shared).

## 6. Accessibility declaration

Play will ask about the AccessibilityService. Use the declaration text in `LISTING.md`, and record a short
video (30–60 s) on your phone showing:

1. Settings → Pause → Pause everywhere → the in-app disclosure → "Agree and turn on" → Android Accessibility settings.
2. Opening a paused app (e.g. Instagram) from a notification or Recents → the Unloop pause screen appears.
3. Double-tap on home → screen locks; swipe down on home → notifications open.

Upload it to YouTube as **Unlisted** and paste the link.

## 7. Submit for review

Publishing overview → **Send for review**. First reviews of apps using Accessibility often take several days.
