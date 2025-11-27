#!/bin/zsh

# Clean and build the mod (clean ensures only one JAR in build/libs/)
# Tests run automatically (85 tests, ~6s)
./gradlew clean build -Pdevelopment='true'

# Remove all old versions of the mod from mods folder
rm -f ../minecraft/mods/multivillageselector-*.jar

# Copy the newly built JAR (using wildcard to be version-agnostic)
cp build/libs/multivillageselector-*.jar ../minecraft/mods/

# Optionally delete config to force regeneration (uncomment if needed):
#rm -f ../minecraft/config/multivillageselector.json5

echo "✅ Mod built and installed!"
echo "⚠️  Restart Minecraft for changes to take effect"