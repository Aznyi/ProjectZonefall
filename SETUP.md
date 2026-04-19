# Zonefall

Zonefall is a Minecraft Java Edition multiplayer extraction-survival prototype built as a Paper plugin.

Current target:

- Minecraft Java Edition 1.21.11
- Paper 1.21.11
- Java 21
- Gradle build

This repository is set up for local Paper plugin development first. The current Phase 1 prototype supports a command-driven test match, a shrinking world border, basic hostile mob pressure, extraction zones, and stub services for future profile, stash, crafting, party, and matchmaking systems.

## Requirements

- Minecraft Java Edition 1.21.11 client
- Paper 1.21.11 server
- Amazon Corretto 21 or another Java 21 JDK
- PowerShell on Windows, or a normal shell on macOS/Linux

The repository includes the Gradle wrapper, so a global Gradle install is not required for normal builds.

## Install Java 21

### Windows

Install Amazon Corretto 21 from:

https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html

After installation, Corretto is commonly installed under:

```text
C:\Program Files\Amazon Corretto\jdk21.x.x_x
```

In PowerShell, you can check the installed folder:

```powershell
Get-ChildItem "C:\Program Files\Amazon Corretto"
```

For the current PowerShell session, set `JAVA_HOME` and update `Path`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk21.x.x_x"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Replace `jdk21.x.x_x` with the actual folder name on your machine.

Verify Java:

```powershell
java -version
javac -version
```

Both commands should report Java 21.

### macOS/Linux

Install a Java 21 JDK using your preferred package manager or Amazon Corretto distribution.

Verify:

```bash
java -version
javac -version
```

Both commands should report Java 21.

## Build The Plugin

From the repository root:

### Windows

```powershell
.\gradlew.bat clean build
```

### macOS/Linux

```bash
./gradlew clean build
```

The built plugin jar will be created under:

```text
build/libs/
```

For the current project version, the jar name is similar to:

```text
Zonefall-0.1.0-SNAPSHOT.jar
```

## Install The Plugin On A Paper Server

1. Stop the Paper server if it is running.
2. Copy the built Zonefall jar into the server's `plugins` folder.
3. Start the Paper server.

Example from a repository that is also being used as the local Paper server folder:

### Windows

```powershell
Copy-Item .\build\libs\Zonefall-0.1.0-SNAPSHOT.jar .\plugins\Zonefall.jar -Force
```

### macOS/Linux

```bash
cp build/libs/Zonefall-0.1.0-SNAPSHOT.jar plugins/Zonefall.jar
```

If your Paper server is in a different folder, copy the jar to that server's `plugins` directory instead:

```text
your-paper-server/
`-- plugins/
    `-- Zonefall.jar
```

## Start A Local Paper Server

From the Paper server folder:

### Windows

```powershell
java -Xms2G -Xmx4G -jar paper-1.21.11.jar nogui
```

### macOS/Linux

```bash
java -Xms2G -Xmx4G -jar paper-1.21.11.jar nogui
```

Use the actual Paper jar filename in your server folder.

On first server setup, Paper/Minecraft requires accepting the EULA. Open `eula.txt`, set:

```text
eula=true
```

Then start the server again.

## Confirm The Plugin Loaded

In the server console, look for:

```text
Enabling Zonefall
Zonefall enabled. Phase 1 prototype ready.
```

In game or console, you can also run:

```text
/plugins
```

Zonefall should appear in the plugin list.

## Op Yourself For Local Testing

In the server console:

```text
op YourMinecraftName
```

The prototype command currently requires:

```text
zonefall.admin
```

Operators receive this permission by default.

## Prototype Test Flow

Join the server, stand where you want the test match center to be, then run:

```text
/zonefall help
/zonefall create
/zonefall join
/zonefall status
/zonefall start
```

Expected behavior:

- `/zonefall create` creates one local test match in the current world.
- `/zonefall join` adds you to the match.
- `/zonefall start` begins the countdown.
- When the countdown ends, the match becomes active.
- The world border centers near the match start location and shrinks over time.
- Basic hostile mobs periodically spawn near active players.
- A green particle extraction zone appears near the match center.
- Entering the extraction zone extracts the player.

You can also manually extract while standing inside the extraction zone:

```text
/zonefall extract
```

With one player in the match, successful extraction should end the match.

Force-stop a test match:

```text
/zonefall stop
```

Print current debug state:

```text
/zonefall status
```

## Configuration

The default plugin config is stored at:

```text
src/main/resources/config.yml
```

After the plugin runs on a server, Paper copies it to:

```text
plugins/Zonefall/config.yml
```

Useful Phase 1 values:

```yaml
debug: true

match:
  countdown-seconds: 10
  duration-seconds: 600
  restore-border-on-end: true

zone:
  border-start-size: 500.0
  border-end-size: 75.0

pve:
  mob-spawn-interval-seconds: 20
  mobs-per-player: 2
  spawn-min-distance: 12.0
  spawn-max-distance: 24.0

extraction:
  radius: 5.0
  vertical-tolerance: 4.0
  hold-seconds: 0
```

Restart the server after changing config values.

## If Gradle Is Not Installed

Normal builds should use the included Gradle wrapper:

```powershell
.\gradlew.bat clean build
```

or:

```bash
./gradlew clean build
```

If the wrapper files are missing for some reason, install Gradle manually, then regenerate them:

```bash
gradle wrapper --gradle-version 8.10.2
```

After that, use the wrapper again instead of relying on a global Gradle install.

## Current Phase 1 Scope

Implemented:

- Paper plugin bootstrap
- `/zonefall` command surface
- One local active test match
- Match state machine
- Countdown and timed match loop
- Shrinking world border
- Basic hostile mob spawning
- Simple extraction zone
- Match result summary
- In-memory profile/stash placeholders
- Crafting, matchmaking, and party service stubs

Deferred:

- Persistent stash storage
- Loot tables
- Loot-on-death as a custom tracked loot system
- Crafting and upgrade economy
- Backpacks and carry capacity
- Objective rewards
- Multiple simultaneous matches
- Arena world cloning or instance management
- Real parties and matchmaking

TEST
