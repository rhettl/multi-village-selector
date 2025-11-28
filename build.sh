#!/bin/zsh

# Clean and build the mod (clean ensures only one JAR in build/libs/)
# Tests run automatically (145 tests, ~6s)
./gradlew clean build -Pdevelopment='true'

# === NeoForge ===
# Remove old versions and copy new JAR
rm -f ../minecraft/mods/multivillageselector-*.jar
cp neoforge/build/libs/multivillageselector-neoforge-*.jar ../minecraft/mods/
# Exclude dev/shadow/sources JARs
rm -f ../minecraft/mods/multivillageselector-*-dev*.jar ../minecraft/mods/multivillageselector-*-sources.jar

# Optionally delete config to force regeneration (uncomment if needed):
#rm -f ../minecraft/config/multivillageselector.json5

# === Fabric ===
# Remove old versions and copy new JAR
rm -f ../minecraft-fabric/mods/multivillageselector-*.jar
cp fabric/build/libs/multivillageselector-fabric-*.jar ../minecraft-fabric/mods/
# Exclude dev/shadow/sources JARs
rm -f ../minecraft-fabric/mods/multivillageselector-*-dev*.jar ../minecraft-fabric/mods/multivillageselector-*-sources.jar

# Optionally delete config to force regeneration (uncomment if needed):
#rm -f ../minecraft-fabric/config/multivillageselector.json5

echo "✅ Mod built and installed to both NeoForge and Fabric!"
echo "⚠️  Restart Minecraft for changes to take effect"