# Releasing Sumi

Everything needed to put Sumi on Google Play, and to cut every release after the
first. Work top to bottom the first time; after that only **Cutting a release**
matters.

The console steps are yours to do by hand. Nothing in this repository talks to
Google Play, and no automated release exists on purpose: an upload cannot be
undone.

---

## 1. Before the first upload

| What | Where | Note |
| --- | --- | --- |
| Google Play developer account | [play.google.com/console](https://play.google.com/console) | One time, 25 USD. Identity verification can take a few days, so start it first. |
| Google Cloud project with the OAuth consent screen **published** | [console.cloud.google.com](https://console.cloud.google.com) | Section 5. Sync stays broken for everyone else until this is done. |
| Privacy policy on a public URL | `docs/privacy.html` in this repository | Turn on GitHub Pages for `/docs` and it is served at `https://prsrwt.github.io/Sumi/privacy.html`. Fill in `CONTACT_EMAIL` in that file first. |
| The upload keystore, backed up somewhere that is not this computer | `~/.sumi-signing/` | Lose it and you cannot upload another build to this listing. Back it up offline, not into the synced folder. |

---

## 2. Cutting a release

1. **Raise the numbers.** Edit `version.properties` at the top of the repository.
   `versionCode` must rise by at least one for every upload to any track, and can
   never be reused. `versionName` is what people read.

2. **Build and check.** From the repository root:

   ```bash
   ./gradlew clean :app:testDebugUnitTest :app:lintRelease :app:bundleRelease
   ```

   The build refuses to continue if it finds an em dash anywhere in the project,
   which is deliberate. Lint failures and test failures both stop the build.

3. **Test the real thing on a phone.** R8 shrinks and rewrites the release build,
   and breakages there never appear in debug:

   ```bash
   ./gradlew :app:assembleRelease
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

   Walk the introduction, log an hour from the widget, open Balance, and if the
   account is connected, force a sync.

4. **Upload** `app/build/outputs/bundle/release/app-release.aab`. Google Play takes
   App Bundles, not APKs. The APK above is for your own testing only.

5. **Tag the commit** so the uploaded build can be found again:

   ```bash
   git tag -a v1.0.0 -m "Sumi 1.0.0"
   ```

---

## 3. Play App Signing, and the one thing that breaks sync

When you upload the first bundle, Google Play takes over signing. Your keystore
becomes the **upload key**; Google re-signs the app with its own **app signing
key** before sending it to phones.

That means the certificate on a phone that installed Sumi from Play is **not** the
one you built with. Google identifies an Android OAuth client by package name plus
signing certificate, so unless you register Play's certificate too, signing in to
Google Sheets fails for every user who installed from the store, while it keeps
working on your own device.

After the first upload:

1. In Play Console, open **Test and release, Setup, App integrity, App signing**.
2. Copy the **SHA-1 certificate fingerprint** of the app signing key.
3. In the Google Cloud console, under **APIs and services, Credentials**, add an
   **Android** OAuth client with package `io.github.prsrwt.sumi` and that SHA-1.
4. Keep the existing clients for your debug and release keys. One client per
   certificate is normal, and all of them can coexist.
5. Verify by installing from the internal testing track and connecting Sheets.

---

## 4. Store listing

Paste ready. Keep the wording calm: the app makes no promises about productivity,
and the listing should not either.

**App name** (30 characters)

```
Sumi
```

**Short description** (80 characters)

```
A quiet record of where your time goes.
```

**Full description** (4000 characters)

```
Sumi is a time log that never asks you to be better at anything.

A small widget sits on your home screen and, now and then, quietly becomes a
question: what was that hour? Answer it in a word and the hour is recorded. Ignore
it and nothing is lost, nothing is broken, and nothing nags.

Five elements, not fifty categories
Your time is sorted into five: earth, water, fire, wind and void. You choose what
each one holds, either by turning a wheel of ready made lives, or by naming them
yourself. Under each element sit the parts of your life, Health or Work or Land and
livestock, and under those the words you actually use: run, thesis, chai with dad.
A word is one tap, and it knows where it belongs.

Balance you can see without a number
The Balance screen draws a pentagon. Each spoke is one element, and its length is
how much of your time that element has held. No score, no target shape, no streak
to keep alive. An uneven pentagon is what a real week looks like. Sumi will tell
you when one element has been quiet lately, and when one has been taking far too
much, and it says both things once, calmly, and then stops talking.

Your own copy, in your own Drive
If you want a copy you can keep, connect Google Sheets. Sumi writes your log into a
spreadsheet it creates in your own Google Drive, a tab for each month, and updates
it in the background. It asks for one narrow permission, which lets it touch only
the file it made. The spreadsheet is yours: open it, chart it, share it or delete
it.

Quiet by design
No account. No advertising. No analytics. No tracking of any kind. Sumi has no
servers, so there is nowhere for your time to go except your phone and, if you ask
for it, your own spreadsheet. It works fully offline.

Inside the app there is also a study guide: seventeen chapters explaining how Sumi
was built and why each decision was made, with the real code.

Free, open source, and made to be left alone for a week without guilt.
```

**Category:** Productivity

**Tags:** time management, journal, habit (choose from the list Play offers)

**Contact details:** the email you decide to publish, plus
`https://prsrwt.github.io/Sumi/` as the website.

**Graphics**

| Asset | Exact requirement | Note |
| --- | --- | --- |
| App icon | 512 x 512 PNG, 32 bit, no transparency | Play adds its own rounding. |
| Feature graphic | 1024 x 500 PNG or JPEG | Shown at the top of the listing. Required. |
| Phone screenshots | 2 to 8, PNG or JPEG, 16:9 or 9:16, each side between 320 and 3840 px | **Aspect cannot exceed 2:1.** A raw 1080 x 2400 phone capture is 1:2.22 and is rejected. Crop or letterbox to 1080 x 2160 or 1080 x 1920. |
| Tablet screenshots | Optional | Skip unless you want the tablet badge. |

---

## 5. Google OAuth consent screen

Sync is dead in the water without this, and it is independent of Play.

1. In the Google Cloud console, open **APIs and services, OAuth consent screen**.
2. User type **External**.
3. App name **Sumi**, support email, developer contact email.
4. App domain and privacy policy link: `https://prsrwt.github.io/Sumi/privacy.html`.
5. Scopes: add only `https://www.googleapis.com/auth/drive.file`. Do not add the
   Sheets scope. Sumi reaches Sheets through files it created itself, which
   `drive.file` already covers, and the Sheets scope would drag the app into a far
   heavier review.
6. Set publishing status to **In production**.

While the consent screen is in **Testing**, only the accounts you list can sign in
and their grants expire after seven days. That is the usual reason sync appears to
work for the developer and for nobody else.

`drive.file` is not a restricted scope, so this normally does not require the
security assessment that broader Drive scopes do. Google may still ask you to
verify ownership of the domain in the listing; publishing the privacy policy page
first makes that straightforward.

---

## 6. App content declarations

Play Console, **Policy and programmes, App content**. Answers specific to Sumi:

| Question | Answer |
| --- | --- |
| Privacy policy | `https://prsrwt.github.io/Sumi/privacy.html` |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access. Sync uses the user's own Google account; no credentials are needed to review the app |
| Content rating | Complete the IARC questionnaire. Everything is no: no violence, no user communication, no purchases. Expect Everyone / PEGI 3 |
| Target audience | 18 and over. Selecting 13 to 17 pulls the app into the Families policy, which is extra work for nothing here |
| News app | No |
| COVID-19 contact tracing | No |
| Data safety | Section 7 |
| Government app | No |
| Financial features | None |
| Health apps | No. Sumi records time, not health data |
| Account deletion | Not applicable: Sumi creates no account. The Google sign-in is authorization for the user's own Drive, not a Sumi account |

---

## 7. Data safety

Play defines collection as user data leaving the device **to you, or to a service
acting for you**. Sumi sends nothing to the developer and has no server. The only
transfer is to a spreadsheet in the user's own Drive, begun by the user, visible to
the user, and deletable by the user.

**Recommended answers**

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | No |
| Is all of the user data collected by your app encrypted in transit? | Not applicable, and the Drive calls are HTTPS regardless |
| Do you provide a way for users to request that their data be deleted? | Not applicable. Uninstalling clears the phone; the user deletes the spreadsheet from their own Drive |

State plainly in the listing and the policy that the developer receives nothing.
Both documents here already do.

**If a reviewer reads it the other way.** Some reviewers treat any off-device
transfer as collection. The fallback, which is honest and which does not
contradict the privacy policy, is to declare:

- **Personal info, email address**: collected, not shared, optional, purpose App
  functionality (it is remembered so Sumi keeps writing to the same sheet).
- **App activity, other user-generated content**: collected, not shared, optional,
  purpose App functionality (the log written to the user's spreadsheet).

Both marked as optional, because Sumi is fully usable without ever connecting an
account. Over-declaring here is safe. Under-declaring is what gets apps pulled.

---

## 8. Testing tracks, then production

Developer accounts registered as individuals in recent years must run a closed test
before they can apply for production access: **12 testers who stay opted in for 14
continuous days**. Confirm the current numbers in the console, since Google adjusts
them.

A workable order:

1. **Internal testing.** Up to 100 testers, available in minutes. Use this to prove
   the Play signed build can still sign in to Google Sheets (section 3).
2. **Closed testing.** Create a tester list, invite the twelve, leave it running for
   the full fourteen days. Ask them to actually open the app: silent installs are
   what fail the requirement.
3. **Apply for production access.** The console asks what you learned from the test.
   Answer it seriously, in a few sentences.
4. **Production.** Roll out at 20 percent for the first release. A staged rollout
   can be halted; a full one cannot.

---

## 9. Handing it out from GitHub, before the store

A GitHub release is the quickest honest way to let people try Sumi. They download
one file and install it themselves.

1. Build the **APK**, not the bundle. People cannot install an `.aab`.

   ```bash
   ./gradlew :app:assembleRelease
   cp app/build/outputs/apk/release/app-release.apk sumi-1.0.0.apk
   ```

2. Tag the commit you built, and push the tag.
3. On github.com, open **Releases, Draft a new release**, choose that tag, attach
   `sumi-1.0.0.apk`, and paste the notes below.
4. The link to hand out is the release page, not the raw file, so people can read
   what they are installing.

Keep using the same keystore for every build you publish this way. An APK signed
with a different key will not install over an earlier one, and the only way out is
for people to uninstall and lose their log.

**Notes to paste**

```text
Sumi 1.0.0

A quiet time log for Android. A widget on your home screen becomes a question now
and then; answer it in a word and the hour is recorded. Balance draws a pentagon
of where your time actually went. No account, no ads, no analytics, no streaks.

Install
1. Download sumi-1.0.0.apk below.
2. Open it. Android will ask whether to allow installs from your browser or files
   app; that prompt is normal for anything not from the Play Store.
3. Android 8 and newer. Nothing else is needed.

Optional Google Sheets sync
Sumi can copy your log into a spreadsheet in your own Google Drive. It asks for one
narrow permission and can only see the file it created. While Sumi is in Google's
review queue, sign-in works only for accounts I have added as testers, so if you
want sync, ask me. Everything else works fully offline.

What it does not do
No account. No advertising. No analytics. No tracking. Nothing leaves your phone
unless you connect the sheet yourself.

Privacy policy: https://prsrwt.github.io/Sumi/privacy.html
How it is built, chapter by chapter: https://prsrwt.github.io/Sumi/
```

Two things to know before you point people at it:

- **Sheets sync is capped until the consent screen is published.** In Testing mode
  Google allows 100 named testers and their permission expires after seven days.
  Section 5 fixes that, and it is worth doing before any post that might bring
  people in.
- **The APK you publish is signed with your own key.** Google Play will later sign
  its copies with a different one (section 3), so a person who installs from GitHub
  and later installs from Play will have to uninstall first. Worth a line in the
  release notes when that day comes.

## 10. After every release

- Raise `versionCode` before the next build, always.
- Tag the commit you shipped.
- Keep `docs/privacy.html` true. If Sumi ever sends something new anywhere, that
  page changes in the same commit, and its effective date changes with it.
