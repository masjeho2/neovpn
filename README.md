# NeoVPN

Android VPN client based on Xray-core with modern UI.

![NeoVPN](https://img.shields.io/badge/NeoVPN-v1.0.4-blue) ![Android](https://img.shields.io/badge/Android-6.0+-green) ![License](https://img.shields.io/badge/License-MIT-yellow)

## Features

- 🔐 **Multi-Protocol** - VMess, VLESS, Trojan, Shadowsocks
- 🌐 **Transport** - WebSocket, gRPC, HTTP/2
- 📱 **QR Scanner** - Auto-detect QR code with flash support
- 📋 **Clipboard Import** - Paste config from clipboard
- 🔗 **Subscription** - Import subscription URLs
- ⚡ **Quick Settings** - Toggle VPN from notification shade (cat icon 🐱)
- 📊 **Connection Log** - View Xray access & error logs
- 📶 **Quota** - Show remaining data for Jeho-style subscription URLs
- 🎨 **Modern UI** - Material Design with dark theme

## Screenshots

| Home | Server List | QR Scanner |
|------|-------------|------------|
| ![Home](screenshots/home.png) | ![Servers](screenshots/servers.png) | ![Scanner](screenshots/scanner.png) |

## Download

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen)](https://neovpn.x-me.cloud)

## Requirements

- Android 6.0 (API 23) or higher
- Internet connection

## Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35

### Build

```bash
# Clone repository
git clone https://github.com/masjeho2/NeoVPN.git
cd NeoVPN

# Build debug APK (arm)
./gradlew assembleArmDebug

# Build release APK (arm)
./gradlew assembleArmRelease
```

The APK will be at `app/build/outputs/apk/`

## Configuration

### VPN Protocols

| Protocol | Transport | Security |
|----------|-----------|----------|
| VMess | WS, gRPC, H2 | TLS, Reality |
| VLESS | WS, gRPC, H2 | TLS, Reality, None |
| Trojan | WS, gRPC, H2 | TLS |
| Shadowsocks | - | - |

### Settings

- **MTU**: Default 1500
- **SOCKS Port**: Default 10808
- **HTTP Port**: Default 10809
- **VPN DNS**: Default 8.8.8.8
- **Core Log Level**: warning/info/debug
- **Allow Insecure**: Default ON

## Architecture

```
NeoVPN/
├── app/src/main/java/com/neovpn/
│   ├── dto/           # Data models (Profile, Subscription, Quota)
│   ├── fmt/           # Config parsers
│   ├── handler/       # MMKV, settings, Xray JSON
│   ├── network/       # Quota HTTP client
│   ├── receiver/      # Boot auto-connect
│   ├── service/       # VPN & Tile services
│   ├── ui/            # Activities & Adapters
│   └── util/          # Speed, quota format
├── app/src/main/java/com/v2ray/ang/service/  # hev-socks5-tunnel JNI
├── app/src/main/res/  # Resources
└── app/libs/          # Native libraries
    ├── libv2ray.aar   # Xray-core
    └── */libhev-socks5-tunnel.so
```

## Libraries

| Library | Purpose |
|---------|---------|
| [Xray-core](https://github.com/XTLS/Xray-core) | Proxy core |
| [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) | TUN to SOCKS5 |
| [MMKV](https://github.com/Tencent/MMKV) | Key-value storage |
| [ZXing](https://github.com/zxing/zxing) | QR code scanning |
| [OkHttp](https://github.com/square/okhttp) | HTTP client |
| [Glide](https://github.com/bumptech/glide) | Image loading |

## Permission

- `INTERNET` - Network access
- `FOREGROUND_SERVICE` - VPN service
- `POST_NOTIFICATIONS` - VPN notification
- `CAMERA` - QR code scanning
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [v2rayNG](https://github.com/2dust/v2rayNG) - VPN client reference
- [NekoBox](https://github.com/MatsuriDayo/NekoBoxForAndroid) - Scanner implementation
- [JehoStore](https://github.com/masjeho2/jeho-store-v3) - Base project

## Support

If you find this project helpful, please give it a ⭐ on GitHub!

---

**NeoVPN** - Simple, Fast, Secure 🚀
