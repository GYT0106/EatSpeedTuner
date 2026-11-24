# Eat Speed Tuner

**One multiplier controls how long it takes to eat and drink.**

<img src="eatspeedtuner_icon.png" alt="Eat Speed Tuner icon" width="128" height="128">

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-orange)
![Side](https://img.shields.io/badge/Side-Client%20%2B%20Server-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## What it does

Eat Speed Tuner scales the duration of **every** eat and drink action by a single number.
It does not add food, does not change nutrition values, and does not touch combat items.

The formula is simply:

```
new duration = original duration / multiplier
```

| Multiplier | Effect | Steak (1.6 s) | Dried Kelp (0.8 s) |
|:---:|---|---|---|
| `1.0` | vanilla speed (default) | 1.6 s | 0.8 s |
| `0.5` | **half the time** (faster) | 0.8 s | 0.4 s |
| `0.25` | quarter of the time | 0.4 s | 0.2 s |
| `2.0` | twice the time (slower) | 3.2 s | 1.6 s |

Because the result is always `original / multiplier`, a value of `0.5` means exactly
"half as long" no matter which item you are holding.

---

## Features

- **One-number control** — type a multiplier into a text box. No sliders, no preset list.
- **Mod Menu / Configured compatible** — the settings screen is registered through
  NeoForge's `IConfigScreenFactory`, so it opens both from the mod list and from Configured.
- **Live in multiplayer** — the host's value is broadcast to every player the moment it
  changes. No restart, no re-entering the world, no reconnecting.
- **Server-authoritative** — only the host, the singleplayer owner, or an operator
  (permission level 2) can change the value.
- **14 languages in game** — see [Localisation](#localisation).
- **Config file** — `config/eatspeedtuner-common.toml` for servers and pack authors.
- **Command** — `/eatspeedtuner` and `/eatspeedtuner <multiplier>`.
- **Keybind** — unbound by default; bind it in *Options → Controls → Key Binds → Eat Speed Tuner*.

---

## Supported items

Scaling applies to items whose use animation is **EAT** or **DRINK**, which covers every
vanilla and modded food and drink.

| Scaled | Not scaled |
|---|---|
| All foods (steak, bread, apple, carrot, berries, cookie, melon slice, …) | Bows, crossbows, tridents |
| Stews and soups | Shields |
| Potions, milk buckets, honey bottles | Spyglasses, brushes, goat horns |
| Golden apples (normal and enchanted) | Any other hold-to-use item |

This is deliberate: an item is only treated as food when the game itself classifies it as
food, so utility items are never affected.

---

## Vanilla reference timings

Measured from the 1.21.1 item registry, not from memory:

| Item | Vanilla time | Ticks |
|---|---|---|
| Most foods (steak, bread, apple, berries, carrot, cookie, melon slice, …) | 1.6 s | 32 |
| Dried Kelp | 0.8 s | 16 |

> Note: sweet berries are **1.6 s**, the same as steak. The common belief that they are
> faster is a misconception — dried kelp is the genuinely fast one.

---

## Installation

1. Install **NeoForge 21.1.x** for **Minecraft 1.21.1**.
2. Drop `eatspeedtuner-1.0.0.jar` into your `mods` folder.
3. Start the game.

**This jar is universal** — the same file works on client and server. For multiplayer,
install it on **both** sides so the sync packet is understood.

No other mods or libraries are required.

---

## How to use

### In game (singleplayer / host)

Open the settings with any of these:

- **Mods → Eat Speed Tuner → Config**
- **Configured** (if installed)
- Your keybind, once bound
- Command: `/eatspeedtuner`

Type a number and press **Apply**. It takes effect immediately.

### On a multiplayer server

1. The **host / operator** opens the same screen and sets the value.
2. Every connected player is updated instantly.

If a non-operator tries to change it, the server rejects the request and tells them so.

### Command reference

```
/eatspeedtuner            → show the current multiplier
/eatspeedtuner 0.5        → set the multiplier to 0.5
/eatspeedtuner 1          → back to vanilla speed
```

Permission level 2 is required. Singleplayer and the host have it by default.

### Config file

`config/eatspeedtuner-common.toml`

```toml
# Multiplier = original duration / new duration
# 1.0 = vanilla, 0.5 = half the time, 2.0 = twice the time
# Range: 0.01 ~ 100.0
eat_time_multiplier = 1.0
```

Editing this file and reloading the world (or running `/reload`) also works, and the
change is broadcast to players exactly like an in-game edit.

---

## Multiplayer behaviour

| Situation | What happens |
|---|---|
| Singleplayer | The local config is the truth; changes apply instantly. |
| LAN / dedicated server, host edits | Value is saved, applied locally, and broadcast to all players at once. |
| Player joins a server | The server sends the current value on join; the client uses it immediately. |
| Player leaves the server | The client falls back to its own local config. |
| Client without the mod joins | The server skips the sync for that player — no kick, no error. |

The server never trusts a client-supplied value: every change request is re-validated for
permission and clamped to the allowed range before being saved.

---

## Localisation

The in-game interface, commands and keybind names are translated into **14 languages**:

| | | | |
|---|---|---|---|
| English (US) | English (UK)* | 简体中文 | 繁體中文 |
| 日本語 | 한국어 | Русский | Українська |
| Deutsch | Español | Français | Português (Brasil) |
| Italiano | Polski | Türkçe | |

\* falls back to US English.

Translations cover the config screen, the status messages, the command feedback and the
keybind category. The mod-list description itself is English only, because NeoForge reads
that from its own language files rather than from the mod.

---

## Compatibility

- **Minecraft:** 1.21.1
- **Loader:** NeoForge 21.1+ (built and tested against 21.1.250)
- **Side:** client and server (universal jar)
- **Dependencies:** none
- **Mixin-free** — the mod hooks NeoForge's `LivingEntityUseItemEvent.Start`, which fires on
  both sides. There is no bytecode patching, no refmap, and no mixin conflict with other mods.

### Known interactions

- Mods that also change food durations (saturation or hunger tweaks, for example) will
  stack **multiplicatively** with this one, because both edit the same start duration.
- Mods that add **new** foods are scaled automatically — no configuration needed.
- **"Anything Eat" style mods are fully compatible.** This mod registers its duration
  handler at `EventPriority.LOWEST`, so it always runs *after* any mod that establishes a
  base eat duration, and therefore scales the final value. Verified end-to-end: with a 5×
  multiplier, a whitelisted non-food item and a vanilla steak both come out at the same
  scaled duration.

---

## Technical notes

NeoForge fires `LivingEntityUseItemEvent.Start` from inside
`LivingEntity#startUsingItem`, on **both** the client and the server. The event's
`setDuration(int)` directly determines the value written into `useItemRemaining`, which the
game then counts down once per tick. Scaling that number therefore scales the real eat time,
identically on both sides.

That is why this mod needs no Mixin at all: it simply rewrites the duration before the game
starts counting.

---

## Links

- **Source and issue tracker:** use the CurseForge project page.
- **License:** MIT — free to include in modpacks, no permission needed.

---

## Changelog

### 1.0.0

- Initial release for Minecraft 1.21.1 / NeoForge 21.1.
- Multiplier from 0.01 to 100, editable in game, via command, or in the config file.
- Live multiplayer sync with server-side permission checks.
- In-game interface translated into 14 languages.
