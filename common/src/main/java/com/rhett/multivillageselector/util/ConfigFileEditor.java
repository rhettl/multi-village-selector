package com.rhett.multivillageselector.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles config file manipulation (insertion, replacement of sections).
 * Extracted from PlacementCommands for testability.
 */
public class ConfigFileEditor {

    /**
     * Insert or replace placement section in config content.
     * Tries to preserve other content and comments.
     *
     * @param content          The existing config file content
     * @param placementSection The new placement section to insert
     * @return The updated config content
     */
    public static String insertOrReplacePlacement(String content, String placementSection) {
        // Pattern to match existing placement section (handles JSON5 with comments)
        // Matches: placement: { ... }, (with possible nested braces)
        Pattern existingPattern = Pattern.compile(
            "(^|\\n)(\\s*)placement\\s*:\\s*\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\},?\\s*\\n?",
            Pattern.MULTILINE
        );

        Matcher matcher = existingPattern.matcher(content);
        if (matcher.find()) {
            // Replace existing placement section
            return matcher.replaceFirst("$1" + Matcher.quoteReplacement(placementSection) + "\n");
        }

        // No existing placement section - find insertion point
        return insertBeforeDebugOrEnd(content, placementSection);
    }

    /**
     * Find appropriate insertion point and insert content.
     */
    private static String insertBeforeDebugOrEnd(String content, String section) {
        // Look for biome_frequency section end, insert after it
        Pattern biomeFreqEnd = Pattern.compile("(biome_frequency\\s*:\\s*\\{[^{}]*\\},?)\\s*\\n", Pattern.MULTILINE);
        Matcher biomeFreqMatcher = biomeFreqEnd.matcher(content);
        if (biomeFreqMatcher.find()) {
            int insertPos = biomeFreqMatcher.end();
            return content.substring(0, insertPos) + "\n" + section + "\n" + content.substring(insertPos);
        }

        // Look for debug_cmd or debug_logging, insert before it
        Pattern debugPattern = Pattern.compile("(\\n)(\\s*)(debug_cmd|debug_logging)\\s*:", Pattern.MULTILINE);
        Matcher debugMatcher = debugPattern.matcher(content);
        if (debugMatcher.find()) {
            int insertPos = debugMatcher.start() + 1; // After the newline
            return content.substring(0, insertPos) + "\n" + section + "\n" + content.substring(insertPos);
        }

        // Last resort: insert before final closing brace
        int lastBrace = content.lastIndexOf('}');
        if (lastBrace > 0) {
            return content.substring(0, lastBrace) + "\n" + section + "\n" + content.substring(lastBrace);
        }

        // Shouldn't happen with valid config
        return content + "\n" + section;
    }

    /**
     * Build placement section content from registry values.
     *
     * @param setId      Structure set ID
     * @param spacing    Spacing value
     * @param separation Separation value
     * @param salt       Salt value
     * @param spreadType Spread type (linear/triangular)
     * @param exclusionZone Optional exclusion zone info (null if none)
     * @return Formatted placement entry for config
     */
    public static String buildPlacementEntry(String setId, int spacing, int separation,
                                             int salt, String spreadType,
                                             ExclusionZoneInfo exclusionZone) {
        StringBuilder sb = new StringBuilder();
        sb.append("    \"").append(setId).append("\": {\n");
        sb.append("      spacing: ").append(spacing).append(",\n");
        sb.append("      separation: ").append(separation).append(",\n");
        sb.append("      salt: ").append(salt).append(",\n");
        sb.append("      spreadType: \"").append(spreadType).append("\",\n");

        if (exclusionZone != null) {
            sb.append("      exclusion_zone: {\n");
            sb.append("        other_set: \"").append(exclusionZone.otherSet).append("\",\n");
            sb.append("        chunk_count: ").append(exclusionZone.chunkCount).append(",\n");
            sb.append("      },\n");
        }

        sb.append("    },\n");
        return sb.toString();
    }

    /**
     * Build complete placement section header and footer.
     */
    public static String buildPlacementSectionStart() {
        return "  // Placement values from registry (edit to override)\n  placement: {\n";
    }

    public static String buildPlacementSectionEnd() {
        return "  },\n";
    }

    /**
     * Exclusion zone info for placement entries.
     */
    public static class ExclusionZoneInfo {
        public final String otherSet;
        public final int chunkCount;

        public ExclusionZoneInfo(String otherSet, int chunkCount) {
            this.otherSet = otherSet;
            this.chunkCount = chunkCount;
        }
    }
}
