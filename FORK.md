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

## Install (sideload), side by side with the original

The fork has its own package (`com.bydmate.fork`), launcher name
(**BYDMate Fork**) and its own helper daemon (`bydmatefork_helper`), so it
installs **next to** the original BYDMate. The original stays untouched.

1. Download the APK from
   [Releases → latest-apk](../../releases/tag/latest-apk) and install it the
   way you installed BYDMate (USB / ADB).
2. Open it and go through setup like the first time. It needs its own
   wireless-debugging authorization and permissions.
3. Optional: export a backup from the original and restore it in the fork.

Running both at once works, but don't enable the same feature in both apps
(steering-key bindings, floating widget, cluster projection, voice button,
the same automation rules). They would both react. Use the fork for the new
rules and turn those features off in one of the two.

Each push to `main` or `fork/**` builds a new signed APK through the
*Build APK* workflow. Every build uses the same key, so it installs as an
update to the previous fork build.
