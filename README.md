# FreeChat - Offline P2P Messenger

FreeChat is an Android application built with Kotlin that allows two devices to communicate over a local Wi-Fi network or mobile Hotspot without needing an active internet connection. It uses pure Java Sockets for real-time, peer-to-peer (P2P) communication.

## Features

- **True P2P Communication:** Direct socket connection between Host and Client.
- **Zero Internet Required:** Works entirely on a local network or mobile hotspot.
- **No Data Stored:** Ephemeral messaging. Chats only exist in RAM and are permanently cleared when the app is closed.
- **Background Service:** The socket connection runs in a Foreground Service, ensuring the connection doesn't drop when the app is minimized.
- **Smart Notifications:** Displays custom heads-up notifications for new text and voice messages when the app is in the background.
- **Auto Disconnect Detection:** Implements a bi-directional heartbeat (ping) system and socket timeouts to immediately detect if the other user turns off their Wi-Fi or drops the connection.
- **Voice Messages:** Support for recording and sending audio files over the socket stream.

## How to Use

1. **Connect Devices:** Ensure both Android devices are connected to the same Wi-Fi network, or turn on a Mobile Hotspot on Device A and connect Device B to it.
2. **Setup Host (Device A):**
   - Open the app and click on **"Start Host"**.
   - Note the IP address displayed on the screen.
3. **Setup Client (Device B):**
   - Open the app, enter the Host's IP address in the target IP field.
   - Click **"Connect"** (The app has a 30-second connection timeout).
4. **Chat:** Once connected, the IP field will disappear for privacy, and you can start sending texts and voice notes. If anyone disconnects, the UI automatically resets.

## Tech Stack

- **Language:** Kotlin
- **Architecture:** MVVM (Model-View-ViewModel) with a Singleton Repository for state management.
- **Concurrency:** Kotlin Coroutines (`lifecycleScope`, `Dispatchers.IO`).
- **Networking:** `java.net.Socket` and `java.net.ServerSocket`.
- **Background Tasks:** Android Foreground Services with Notification Channels.

## Privacy Note
This app does not collect, store, or transmit any user data to the internet. All communication is strictly local between the two connected devices.
