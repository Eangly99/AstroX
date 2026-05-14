# Changelog

All notable changes to AstroX are documented in this file.

## [2.0.0] — 2026-05-14

### ✨ New Features

#### Core Engine
- **Multi-Target Batch Processing**: `astrox batch --target-dir <path>` processes all JARs concurrently with `ForkJoinPool` and live ANSI progress table
- **Advanced Bytecode Weaving**: `ClassRemapper` for randomized package names, `AdviceAdapter` for post-`onEnable()` injection (preserves original plugin code)
- **Stealth Mode**: `--stealth` flag hides injected classes from `JarFile.entries()` via ZIP Central Directory patching (APPNOTE.TXT §4.3.12)
- **AES-256-GCM Encryption**: Replaces XOR with authenticated encryption — random 256-bit key + 96-bit IV per injection
- **Plugin Fingerprinting**: SHA-256 fingerprint database (`~/.astrox/known_plugins.json`) warns before injecting into ProtocolLib, LuckPerms, etc.
- **Pipeline Result**: `PipelineResult` record with full injection metadata, serializable to JSON via `--report`

#### Payload System
- **Modular Payload Architecture**: `PayloadModule` interface with `ServiceLoader` discovery
- **FileExfil Module**: Read server files and exfiltrate via Discord webhook
- **EnvDump Module**: Dump JVM system properties and environment variables
- **RCE Module**: Remote command execution with stdout/stderr capture
- **MemoryDump Module**: JVM memory read via `sun.misc.Unsafe`

#### C2 & Security
- **HTTP Long-Polling C2**: Two-way command-and-control channel with exponential backoff
- **Agent Detection**: Detects YourKit, JRebel, BTrace, Arthas, debuggers → enters safe mode
- **Runtime Config Hot-Reload**: `WatchService` on config YAML — reload UUIDs, prefix, URLs without restart

#### Operational Security
- **Build-Time Self-Obfuscation**: `--obfuscate-jar` strips debug info, renames classes/methods, scrambles constant pool
- **Tamper-Evident Audit Log**: HMAC-SHA256 signed JSON-line entries at `~/.astrox/audit.log`
- **Bootstrap Stub**: Reflective `ClassLoader.defineClass()` with runtime-constructed method names

### 🏗️ Architecture
- **Picocli CLI**: Subcommands `inject`, `batch`, `analyze`, `verify-audit`, `fingerprint` with full `--help`
- **SLF4J + Logback**: Colored console output, rolling file logs (30-day retention, gzip), dedicated audit logger
- **Master Key via CLI**: Removed hardcoded `MASTER_KEY` — now required via `--key` flag
- **PipelineResult**: Formal return object from `Injector.inject()` with JSON serialization

### 🔧 Infrastructure
- **GitHub Actions CI/CD**: `build.yml` (compile → test → package), `release.yml` (tag → release), `security-scan.yml` (SpotBugs + OWASP)
- **JUnit 5 Test Suite**: `JarAnalyzerTest`, `StringEncryptorTest`, `PayloadWeaverTest`, `BatchProcessorTest`
- **SpotBugs**: Static analysis integrated into Maven verify phase
- **Dependency Updates**: Jackson 2.17, Picocli 4.7.5, SLF4J 2.0.12, Logback 1.4.14, Commons Compress 1.26

### 🔒 Security
- Hardcoded `MASTER_KEY` removed from `Config.java`
- Input validation: reject JARs > 50MB, reject non-JAR files
- Plugin fingerprint warning system
- AES-256-GCM replaces XOR encryption
- HMAC-signed audit trail

## [1.0.0] — 2025-01-01

### Initial Release
- Basic JAR injection with ASM bytecode manipulation
- XOR string encryption
- Discord webhook notifications
- Manual CLI argument parsing
- 24 built-in commands
