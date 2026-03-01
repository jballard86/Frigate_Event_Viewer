# Frigate Event Viewer

The native, intelligent Android command center for your Frigate NVR system.

Designed as the primary mobile interface for the **[Frigate Event Buffer](https://github.com/jballard86/frigate-event-buffer)**, this app transforms raw security footage into a rich, narrative-driven history of your property. Experience real-time monitoring, AI-summarized daily reviews, and rich interactive notifications directly on your Android device.

---

## Features

### Live Monitoring & Dashboard

- **Low-Latency Live Streams:** View real-time camera feeds powered by Frigate's internal `go2rtc` proxy.
- **Tactical Dashboard:** A real-time overview displaying the most recent high-interest event, daily/weekly stats, and backend system health.
- **Fluid Navigation:** Native Jetpack Compose UI with horizontal swipeable tabs, synced bottom navigation, and full-width swipe-back gestures.

### AI-Powered Event Intelligence

- **Daily Reviews:** Read a concise, Markdown-formatted summary of the day's highlights, categorized by threat level and generated via LLMs.
- **Multi-Cam Evidence Locker:** Seamlessly track subjects across multiple cameras as they move through your property—consolidated into a single cohesive story.
- **Deep Timeline:** Dive into the technical timeline of notifications, video exports, and AI analysis for any given event.

### Rich, Actionable Notifications

- **Phase-Aware FCM Alerts:** Real-time push notifications that update dynamically as an event progresses from a snapshot to a full video clip.
- **Inline Actions:** Mark events as reviewed or keep critical footage permanently, right from the Android notification shade.
- **Unread Badges:** App icon badges stay perfectly synced with your unreviewed event queue for immediate visibility.

### Advanced Event Management

- **Snooze Control:** Temporarily mute notifications or AI processing for specific cameras using convenient presets (30m, 1h, 2h).
- **Smart Retention:** Protect critical footage from automatic cleanup by saving it to a permanent locker.
- **Review Queue:** Easily filter between reviewed and unreviewed events, and clear your queue with a single tap.

---

## Technical Architecture

Built with modern Android development practices to ensure high performance and reliability:

- **100% Kotlin & Jetpack Compose:** A declarative, responsive, and fluid single-Activity user interface.
- **Media3 / ExoPlayer:** High-performance video playback with intelligent aspect-ratio handling and custom streaming thumbnail fetchers.
- **OkHttp & Retrofit:** Fast, connection-pooled networking to interface with the Frigate Event Buffer API.
- **Coil:** Efficient image loading and in-memory caching for snapshots and notification thumbnails.
- **DataStore:** Modern asynchronous preferences management for server configurations and UI state.

---

## Setup & Connectivity

The application is designed for private, secure environments and works seamlessly over local networks or VPNs like Tailscale.

### Prerequisites

- A running instance of [Frigate NVR](https://docs.frigate.video/).
- A running instance of the [Frigate Event Buffer](https://github.com/jballard86/frigate-event-buffer) backend.

### Network Setup & Remote Access

If you are behind a CGNAT or prefer not to use port forwarding, **Tailscale** is the recommended way to securely connect your phone to Frigate and the Event Buffer remotely (traditional port forwarding may also work if your ISP allows it, but Tailscale is the officially tested solution).

**Tailscale Configuration Steps:**

1. **Subnet Route:** Set up a Tailscale subnet route on a machine on your LAN (e.g., your Unraid server).
2. **Backend Access:** The Frigate Event Buffer must be reachable via Tailscale. Use the Tailscale IP of the machine running the backend (e.g., `http://<server-tailscale-ip>:5050`).
3. **Phone Setup:** On your Android device, ensure the Tailscale app is configured to use that subnet route so it can reach your LAN.
4. **Frigate Access:** Frigate must also be on Tailscale (or otherwise reachable via the same Tailscale network).
5. **App Configuration:** In the Android app's settings, use your server's **Tailscale IP** for the Frigate address, instead of the local LAN IP, no port is needed with Frigate the App uses the frigate API nativly.

### Push Notifications (Firebase)

To enable real-time push notifications, the app requires Firebase Cloud Messaging (FCM):

1. Create a project in your Firebase Console and add an Android app.
2. Download the `google-services.json` file.
3. Place it in the `app/` directory of this Android project (it is gitignored by default).
4. Build the app. The Google Services plugin will automatically configure FCM for the project.
5. Generate a new private key from your Firebase project settings (Service Accounts tab) and save it as a JSON file.
6. Place this service account JSON file on your backend server to allow the Frigate Event Buffer to send push notifications to the app.

---

## License & Privacy

This project is built for secure, self-hosted environments. All AI analysis and event processing happens on your configured backend, keeping your data and privacy strictly within your control.