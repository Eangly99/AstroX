# AstroX

Advanced Java tooling for analyzing and instrumenting Minecraft plugin JARs. Built with Maven, Java 17, and ASM. Intended for authorized security research and defensive testing only.

## Features
- JAR analysis: extracts plugin name, version, main class, Java version, and base package
- Bytecode instrumentation powered by ASM (`asm`, `asm-commons`, `asm-util`)
- CLI with runtime configuration flags (prefix, debug mode, propagation toggle)
- Optional Discord webhook notifications
- Fat JAR packaging via Maven Shade Plugin

## Requirements
- Java 17 (`JAVA_HOME` pointing to JDK 17)
- Maven 3.8+

## Build
- `mvn clean package`
- Output: `target/AstroX-1.0.0.jar` (shaded, main class `dev.naruto.astrox.AstroX`)

## Usage
- Syntax: `java -jar AstroX.jar <input.jar> [options]`
- Common options:
  - `--auth <uuid> [uuid2...]` pre-authorize users
  - `--webhook <discord_url>` enable Discord notifications
  - `--prefix <char>` set custom command prefix (default `#`)
  - `--no-propagation` disable auto-spread behavior
  - `--debug` enable verbose logging

## Project Structure
- `src/main/java/dev/naruto/astrox/AstroX.java` entrypoint and CLI
- `src/main/java/dev/naruto/astrox/core/JarAnalyzer.java` plugin metadata analysis
- `src/main/java/dev/naruto/astrox/core/Injector.java` injection orchestration
- `src/main/java/dev/naruto/astrox/core/PayloadWeaver.java` payload generation and weaving
- `src/main/java/dev/naruto/astrox/payload/BackdoorCore.java` payload core runtime

## License
MIT — see `LICENSE`.