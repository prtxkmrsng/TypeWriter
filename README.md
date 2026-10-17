# TypeWriter

An offline, tactile mechanical typewriter replica built for Android tablets and physical keyboards. Crafted by **[@prtxkmrsng](https://github.com/prtxkmrsng)**.

## Features

- **Authentic Carriage & Platen Physics**: Fixed center typehead (`76%` viewport height) with a moving 68-column paper carriage, margin bell at column 60, and 55-line fanfold page perforations.
- **Mechanical Imprint & Hardcore Overstrike Mode**: Deterministic per-character ink-pressure variation, subtle vertical typebar tolerance, bichrome ribbon selector (Black/Red ink), and an optional **Hardcore Mode** where `Backspace` shifts the carriage back to physically overstrike (`x` / `-`) characters.
- **Twilight Torch Mode**: Pitch-dark writing environment illuminated by a flickering, warm tungsten lamp mounted to the typehead.
- **Zero-Asset Procedural Sound Engine**: Real-time 16-bit PCM acoustic synthesizer generating key strikes, spacebar thuds, platen ratchet ticks, carriage return zips, and brass margin bell rings via a pre-warmed `AudioTrack` voice pool.
- **Offline Markdown Storage & Exports**: Uses Android's Storage Access Framework (`DocumentFile`) for real-time `.md` autosaving, plus paginated **PDF** and **HTML** export with native Android Share Sheet integration.

## Hardware Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| `Ctrl + S` | Force immediate manual save to disk |
| `Ctrl + P` | Toggle Manuscript Proof / Preview & Export modal |
| `Ctrl + N` | Load a fresh `.md` manuscript sheet |
| `Ctrl + R` | Toggle Bichrome Ribbon (Black / Red ink) |
| `Ctrl + H` | Toggle Hardcore Overstrike Mode |
| `Ctrl + T` | Toggle Twilight Torch Mode |
| `Home` / `End` | Jump carriage to start / end of current line |
| `PgUp` / `PgDn` | Roll platen up / down by 15 lines |

## License

Licensed under the Apache License, Version 2.0.
