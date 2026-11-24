# Saraiki Pro Keyboard (`سرائیکی پرو کیبورڈ`)

An offline-first, trilingual native Android Input Method Editor (IME) built for **Saraiki (`سرائیکی`)**, **Urdu (`اردو`)**, and **English (`QWERTY`)**, paired with a Jetpack Compose Material 3 companion Settings & Onboarding application.

## Highlights

- **First-Class Saraiki Unicode & Spatial Popup Geometry**:
  - Full support for `ٻ` (U+067B), `ڄ` (U+0684), `ڳ` (U+06B3), `ڱ` (U+06B1), `ڋ` (U+068B), `ݨ` (U+0768), and `ݮ` (U+076E) via extensible `assets/saraiki_char_map.json`.
  - Exact Gboard-style long-press geometry: `ج → ڄ`, `د → ڋ`, `ن → ݨ`, `ب → ٻ` centered above the key; `گ → ڳ` centered directly above `گ` with `ڱ` immediately to its left.
- **4 Primary Character Rows + Bottom Functional Row**:
  - No permanent number row by default (optional toggle in **Settings → Preferences**).
  - Western Arabic numerals (`1 2 3 4 5 6 7 8 9 0`) mapped as long-press alternates on all 10 top-row keys across Saraiki, Urdu, and English—never converted to Eastern Arabic digits.
- **Real-Time Local Word Learning & Frecency Prediction**:
  - Dual-layer in-memory `PrefixTrie` + N-gram cache synced asynchronously with Room/SQLite (WAL mode).
  - Newly typed words appear in predictions on the very next sentence, guarded by a smart Backspace typo-protection filter and one-tap **Forget word** long-press pill.
- **Complete International Feature Suite**:
  - Bézier glide typing, spacebar cursor trackpad, gesture delete with Undo chip, Smart Clipboard Manager (paused in password/incognito fields), offline Emoji/Kaomoji/Symbols drawers, D-Pad Text Editing Pad, Voice Typing (`SpeechRecognizer`), Live Resizer, One-Handed & Floating modes, 5 built-in themes + Custom Theme Studio (with Photo Picker), and synthesized `SoundPool` + `VibrationEffect` haptics.
- **100% Offline Privacy**:
  - Zero internet permissions, zero analytics, and strict `EditorInfo` password/`IME_FLAG_NO_PERSONALIZED_LEARNING` enforcement.

## Build & Test

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
