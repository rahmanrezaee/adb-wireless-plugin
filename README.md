# ADB Wireless Manager
A comprehensive Android Studio / IntelliJ IDEA plugin for managing wireless ADB connections with automatic Android SDK detection and intuitive device management.

## ✨ Features

### 🔧 **Automatic Configuration**
- **Auto-detect Android SDK** - Automatically finds your Android SDK installation
- **Smart ADB Path Detection** - Works with standard Android Studio setups out of the box
- **Cross-platform Support** - Works on Windows, macOS, and Linux

### 📱 **Device Management**
- **Add Multiple Devices** - Save and manage multiple Android devices
- **One-click Connection** - Quick connect/disconnect to saved devices
- **Device Status Monitoring** - Real-time connection status indicators
- **Device Persistence** - Your devices are saved between IDE sessions

### 🔗 **Wireless ADB Operations**
- **Device Pairing** - Pair new devices using Android 11+ wireless debugging
- **Connection Management** - Connect/disconnect from wireless devices
- **Connection Status** - Visual indicators for device connection state
- **Error Handling** - Clear error messages and troubleshooting guidance

### 📊 **Activity Monitoring**
- **Real-time Logging** - See all ADB commands and their output
- **Activity History** - Track connection attempts and results
- **Debug Information** - Detailed logs for troubleshooting
- **Copy/Clear Logs** - Easy log management

### 🎯 **User Experience**
- **Intuitive Tool Window** - Clean, modern interface integrated into your IDE
- **Quick Actions** - Access via Tools menu or toolbar
- **Visual Feedback** - Clear status indicators and progress feedback
- **Responsive Design** - Smooth, fast operations

## 🚀 Getting Started

### Prerequisites
- Android Studio or IntelliJ IDEA (2023.3.1 or later)
- Android SDK with ADB (usually included with Android Studio)
- Android device with wireless debugging enabled (Android 11+)

### Installation

#### Option 1: JetBrains Marketplace (Recommended)
1. Open Android Studio/IntelliJ IDEA
2. Go to **Settings/Preferences** → **Plugins** → **Marketplace**
3. Search for **"ADB Wireless Manager"**
4. Click **Install** and restart your IDE

#### Option 2: Direct from Marketplace
Visit the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28206-adb-wireless-manager) and click **"Install to IDE"**

#### Option 3: Manual Installation
1. Download the latest release from [GitHub Releases](https://github.com/rahmanrezaee/adb-wireless-plugin/releases/latest)
2. Go to **Settings/Preferences** → **Plugins** → **⚙️** → **Install plugin from disk...**
3. Select the downloaded `.zip` file

### Quick Setup

1. **Enable Wireless Debugging on Your Android Device:**
   - Go to **Settings** → **Developer Options** → **Wireless Debugging**
   - Enable **Wireless Debugging**
   - Note the IP address and port shown

2. **Open the Plugin:**
   - Go to **Tools** → **ADB Wireless Manager** or
   - Open the **ADB Wireless** tool window (usually on the right side)

3. **Add Your Device:**
   - Click **"Add New Device"**
   - Enter device name and IP address
   - For new devices: Use **"Pair device with pairing code"** on your phone
   - Enter the pairing port and 6-digit code
   - Click **"Pair Device"**

4. **Connect and Use:**
   - Once paired, click **"Connect"**
   - Your device is now ready for wireless debugging!

## 📖 How to Use

### Adding a New Device
1. Enable wireless debugging on your Android device
2. Click **"Add New Device"** in the tool window
3. Fill in device information:
   - **Device Name**: Any friendly name (e.g., "My Pixel 6")
   - **IP Address**: From your device's wireless debugging settings
   - **Connection Port**: Usually 5555
4. For pairing (first time):
   - Tap **"Pair device with pairing code"** on your device
   - Enter the **Pairing Port** and **6-digit code** shown on your device
   - Click **"Pair Device"**
5. Click **"Connect"** to establish the connection
6. Click **"Save Device"** to remember this device

### Managing Devices
- **Green dot** = Device is connected
- **Red dot** = Device is disconnected
- **Edit** = Modify device settings or reconnect
- **Remove** = Delete device from your list
- **Disconnect** = Disconnect from connected device

### Troubleshooting
- Use **"Refresh List"** to update connection status
- Use **"Restart ADB"** to reset all connections
- Check the **Activity Log** for detailed error information
- Ensure both devices are on the same WiFi network

## 👨‍💻 About the Author

**Rahman Rezaee** - Android Developer & Plugin Creator

- 🔗 **GitHub**: [@rahmanrezaee](https://github.com/rahmanrezaee)
- 📧 **Email**: rahmanrezaie60@gmail.com
- 💼 **Focus**: Android development tools and developer productivity

I created this plugin to solve the daily friction of managing wireless ADB connections during Android development. Having worked extensively with Android apps, I noticed developers often struggled with the command-line approach to wireless debugging. This plugin provides a visual, user-friendly solution that integrates seamlessly into your development workflow.

### Why This Plugin?
- **Personal Need**: Born from my own frustration with manual ADB commands
- **Developer Focus**: Built by a developer, for developers
- **Community Driven**: Open source and welcoming contributions
- **Continuous Improvement**: Regular updates based on user feedback

## 🤝 Contributing

Contributions are welcome! This project is open source and community-driven.

### How to Contribute

1. **Fork the Repository**
   ```bash
   git clone https://github.com/rahmanrezaee/adb-wireless-plugin.git
   cd adb-wireless-plugin
   ```

2. **Set Up Development Environment**
   ```bash
   ./gradlew buildPlugin
   ```

3. **Make Your Changes**
   - Fix bugs, add features, or improve documentation
   - Follow the existing code style
   - Test your changes thoroughly

4. **Submit a Pull Request**
   - Create a descriptive title and detailed description
   - Reference any related issues
   - Include screenshots for UI changes

### Ways to Contribute
- 🐛 **Report Bugs** - Create issues with detailed reproduction steps
- 💡 **Suggest Features** - Share ideas for improvements
- 📝 **Improve Documentation** - Help make the docs clearer
- 🔧 **Fix Issues** - Pick up open issues and submit PRs
- 🌍 **Translations** - Help localize the plugin
- ⭐ **Star the Repo** - Show your support!

### Development Guidelines
- **Code Style**: Follow Kotlin coding conventions
- **Testing**: Test on multiple IDE versions when possible
- **Documentation**: Update README and code comments
- **Compatibility**: Ensure changes work across supported IDE versions

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 Rahman Rezaee

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 🙏 Acknowledgments

- **JetBrains** - For the excellent IntelliJ Platform and plugin template
- **Android Team** - For the ADB wireless debugging capabilities
- **Community Contributors** - For feedback, bug reports, and contributions
- **Beta Testers** - For helping improve the plugin before release

## 📊 Project Stats

- **First Release**: 2024
- **Current Version**: 1.0.2
- **Supported IDEs**: Android Studio, IntelliJ IDEA
- **Minimum IDE Version**: 2023.3.1
- **Language**: Kotlin
- **License**: MIT

## 🔗 Links

- **JetBrains Marketplace**: [ADB Wireless Manager](https://plugins.jetbrains.com/plugin/28206-adb-wireless-manager)
- **GitHub Repository**: [adb-wireless-plugin](https://github.com/rahmanrezaee/adb-wireless-plugin)
- **Issues & Bug Reports**: [GitHub Issues](https://github.com/rahmanrezaee/adb-wireless-plugin/issues)
- **Latest Release**: [GitHub Releases](https://github.com/rahmanrezaee/adb-wireless-plugin/releases/latest)

---

⭐ **If this plugin helps your development workflow, please consider giving it a star on GitHub and rating it on the JetBrains Marketplace!**

Made with ❤️ by [Rahman Rezaee](https://github.com/rahmanrezaee)
