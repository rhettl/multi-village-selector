#!/bin/zsh

./gradlew build
cp build/libs/multivillageselector-1.0.0.jar "../minecraft/mods/"
# Optionally delete config to force regeneration (uncomment if needed):
rm ../minecraft/config/multivillageselector.json5

echo "✅ Mod built and installed!"
echo "⚠️  Restart Minecraft for changes to take effect"