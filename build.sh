#!/bin/zsh

# Clean and build the mod (clean ensures only one JAR in build/libs/)
# Tests run automatically (145 tests, ~6s)
./gradlew clean build -Pdevelopment='true'

# === NeoForge ===
# Remove old versions and copy ONLY the main JAR (not sources/dev/shadow)
rm -f "../minecraft/mods/"multivillageselector-*.jar 2>/dev/null
find neoforge/build/libs -name "multivillageselector-neoforge-*.jar" \
  | grep -v sources | grep -v shadow \
  | xargs -I {} cp {} ../minecraft/mods/

# Optionally delete config to force regeneration (uncomment if needed):
#rm -f ../minecraft/config/multivillageselector.json5

# === Fabric ===
# Remove old versions and copy ONLY the main JAR (not sources/dev/shadow)
rm -f "../minecraft-fabric/mods/"multivillageselector-*.jar 2>/dev/null
find fabric/build/libs -name "multivillageselector-fabric-*.jar" \
  | grep -v sources | grep -v shadow \
  | xargs -I {} cp {} ../minecraft-fabric/mods/

# Optionally delete config to force regeneration (uncomment if needed):
#rm -f ../minecraft-fabric/config/multivillageselector.json5

echo "✅ Mod built and installed to both NeoForge and Fabric!"
echo "⚠️  Restart Minecraft for changes to take effect"