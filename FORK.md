# BYDMate fork: ADAS / CPD switches, webhook, long press

Personal fork of [AndyShaman/BYDMate](https://github.com/AndyShaman/BYDMate)
(PolyForm Noncommercial 1.0.0; not for sale). Adds three things to Automation.

## New actions

**ADAS / CPD switch** (picker → *Driver assist (ADAS)*). About 50 switches taken
from a Seal U FID dump: CPD (child presence detection), driver fatigue and
presence monitors, speed limit warning/assist, lane keep/ELKA, AEB, FCW, BSD,
DOW, RCTA, ESP, HDC, AVH, auto high beam and more.

- Choose **On** or **Off**, or a level for multi-value entries.
- **Raw value** overrides on/off with the exact number written to the car.
- Turning off a safety function (AEB, ESP, ELKA, HDC, PCW, FCW level, brake
  feel) only runs in **P** and always shows a confirmation.

**Webhook** (picker → *Apps*). A POST or GET request to your URL (https only;
http is allowed just for localhost). Leave the body empty to send a JSON
snapshot, or write your own using `{rule} {ts} {soc} {speed} {gear} {mileage}`.
The optional token is sent as `Authorization: Bearer …`. When a rule is shared,
the URL and token are stripped.

## Long press

Both *Widget button* and *Steering-wheel key* triggers have a **Long press**
chip. A short press and a long press on the same button can run different rules.

- Widget buttons: the standard Android long-press.
- Steering keys: once a key has a long-press rule, holding it for 0.8 s fires
  that rule, and the short-press rules fire when you let go. While bound, the
  key's native action is swallowed.
- Some keys send their own keycode for a long press (the right star is 351
  short and 352 long). For those, open *Assign*, **hold** the button, and save
  it as a normal (short) trigger.

## First test in the car: find the on/off values

The dump lists which features exist (FIDs) but not which value means on or off.
The defaults are `1 = on, 2 = off`.

1. Create a rule and add **ADAS / CPD switch → CPD → Off**.
2. Park the car and tap ▶ (*Run now*) on the action row. A toast shows
   `value=2 write=1 status 1→2`:
   - `write=1` means the car accepted it; `0` means it was ignored (no-op).
   - `status a→b` is the switch state before and after the write.
3. Open the car's own settings page to confirm the switch actually changed.
   If it didn't, put `0` in **Raw value** and try again.

## Install (sideload), replacing the original

The fork uses the original package (`com.bydmate.app`) but is signed with the
fork's own key, so Android won't install it as an update over upstream BYDMate.

1. Back up: Settings → Configuration → Save configuration (tick all parts).
   The zip goes to Download.
2. Uninstall the installed BYDMate (and "BYDMate Fork" if you had it).
3. Download the APK from
   [Releases → latest-apk](../../releases/tag/latest-apk) and install it.
4. Go through setup, then Settings → Configuration → Restore config → pick the zip.

The in-app update check follows this fork's releases, not upstream. Each push
to `main` or `fork/**` builds a new APK signed with the same key, so later
fork builds install as updates.

## Steering keys

A rule bound to a steering key now keeps the key service running on its own.
Before this fix it only ran when cluster projection, voice or the knob feature
was on, so bound keys silently stopped working after the car was switched off.
