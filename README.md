# Melon's Anomaly Library

Minecraft **1.20.1** / Forge **47.4.20** / Java **17**.

## Build

Set `org.gradle.java.home` in `gradle.properties` to your local Java 17 installation, then run:

```powershell
.\gradlew.bat build
```

The mod JAR is generated in `build/libs/`.

## Regression tests

```powershell
.\gradlew.bat runGameTestServer -x downloadAssets
```

GameTests and their fixtures are under `src/test/` and are excluded from the production JAR.
