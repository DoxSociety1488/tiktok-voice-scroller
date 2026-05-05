# TikTok Voice Scroller

An Android 15 overlay app that listens for voice commands to scroll TikTok videos hands-free.

## Features

- **Voice-activated scrolling** - Say "skip" or "next" to scroll to the next TikTok video
- **Floating overlay** - Draggable mic indicator shows listening status over any app
- **Continuous listening** - Automatically restarts voice recognition between commands
- **Visual feedback** - Overlay turns green when a skip command is detected

## How It Works

1. **Overlay Service** runs as a foreground service with a floating mic button
2. **SpeechRecognizer** continuously listens for the keywords "skip" or "next"
3. **AccessibilityService** performs a swipe-up gesture on screen to scroll TikTok

## Permissions Required

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the floating mic overlay on top of TikTok |
| `RECORD_AUDIO` | Listen for voice commands via the microphone |
| `FOREGROUND_SERVICE` | Keep the service running in the background |
| `POST_NOTIFICATIONS` | Show the persistent notification (Android 13+) |
| `BIND_ACCESSIBILITY_SERVICE` | Perform swipe gestures to scroll TikTok |

## Setup

1. Open the app and grant all four permissions
2. Enable the "TikTok Voice Scroller" accessibility service in Settings
3. Tap **Start Listening**
4. Open TikTok and say **"skip"** to scroll to the next video

## Building

```bash
# Clone the repo
git clone <repo-url>
cd tiktok-voice-scroller

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35 (Android 15)
- Min SDK: 26 (Android 8.0)

## Voice Commands

| Command | Action |
|---|---|
| "skip" | Scroll to next video |
| "next" | Scroll to next video |
| "skipped" | Scroll to next video |
| "escape" | Scroll to next video |

## Architecture

- `MainActivity` - Permission management UI and service toggle
- `OverlayService` - Foreground service managing the floating overlay and voice recognition
- `VoiceRecognitionManager` - Wraps Android SpeechRecognizer for continuous keyword detection
- `ScrollAccessibilityService` - Performs swipe-up gestures via the Accessibility API

## Target SDK

- **compileSdk**: 35 (Android 15)
- **targetSdk**: 35 (Android 15)
- **minSdk**: 26 (Android 8.0 Oreo)
