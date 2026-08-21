# Manhwa Translator (Android)

An on-device Korean → English tap-to-translate overlay for Android. Draw a box over any
Korean dialogue on screen — in a browser, a comic reader app, anywhere — and get an
instant translation, without leaving the app you're reading in.

## What it does

- Runs as a floating button that sits on top of any other app.
- Tap it once to start a session (grants screen-capture + overlay permission).
- Tap anywhere on screen to draw a translation box over Korean text; the translated
  text appears right next to it, and is also logged in a scrolling panel at the
  bottom of the screen.
- Everything — OCR and translation — runs on-device via ML Kit. No text or screenshots
  are ever uploaded anywhere.

## How to use it

1. **Start:** tap the floating "T" button. Grant the overlay permission (once) and the
   screen-capture permission (each session). The button turns **green** once ready.
2. **Translate:** tap any Korean dialogue on screen.
   - A default-size box appears with its top edge at your tap point; the translation
     shows below (or above, if you tapped low on screen) a moment later.
   - **Resize:** drag the small handle at the box's bottom-right corner.
   - **Move:** drag anywhere else on the box itself.
   - **Draw a custom box from scratch:** double-tap the same spot, then on the second
     tap don't lift your finger — drag to draw the exact rectangle you want.
3. **Pause without stopping:** tap the floating button again to go **amber** — taps now
   pass straight through to the app underneath (e.g. to use its own menus), and the
   bottom panel hides to free up screen space. Tap again to re-arm (green).
4. **Stop:** long-press the floating button (~half a second) to end the session
   entirely (back to **gray**).
5. **Download the language pack ahead of time:** open the app and tap "Download Korean
   Language Pack" before you need it, instead of waiting for it to download silently
   on first use.

## Compatibility

- **Any app or browser, in principle** — the translator captures the screen itself
  (Android's MediaProjection API), so it isn't tied to any specific reader app or
  website. If Korean text is visible on screen, it can be tapped and translated.
- **Scrolling apps (e.g. Komikku) with volume-key or auto-scroll:** fully compatible —
  scrolling that isn't a touch-drag is unaffected by the translator being active.
- **Apps/sites that scroll by touch-drag:** while the translator is "armed" (green),
  it has to intercept taps to know where you tapped, which also intercepts drag
  gestures in the same on-screen area. Toggle to "paused" (amber) to scroll normally,
  then re-arm to translate the next panel. This is a Android platform limitation, not
  a bug — a single overlay can't both catch your taps and pass through your drags at
  the same time.
- **Chrome / any browser:** works the same as any other app, since it's a screen
  overlay, not a browser extension — no install needed in the browser itself.

## Requirements

- Android 8.0 (API 26) or newer.
- Permissions: **Display over other apps** (for the overlay/floating button) and
  **screen recording** (re-requested by Android each time you start a session — this
  is a system requirement, not something the app can skip).

## Known limitations (being actively worked on)

- Occasionally a single tap doesn't capture 100% of a multi-line dialogue box in one
  go — resizing the box (corner-drag) usually fixes it immediately.
- Korean only, currently. See `PROJECT_FROM_SCRATCH.md` for what adding Japanese/
  Chinese would involve.
- The bottom panel and floating button are best-effort background services — Android
  may reclaim them after the app's been fully backgrounded a long time; reopening the
  app restarts them.

## Privacy

All OCR and translation runs on-device via Google's ML Kit. No screenshots, text, or
translations are sent to any server the app controls. ML Kit itself may perform a
one-time model download and periodic remote-config checks over the network — no
screen content is included in those.
