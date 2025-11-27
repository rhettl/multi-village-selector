package com.rhett.multivillageselector.profiler;

import com.rhett.multivillageselector.MultiVillageSelector;
import com.rhett.multivillageselector.config.MVSConfig;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance profiler for chunk structure generation.
 *
 * Compares MVS-controlled structure_sets (e.g., villages) vs vanilla structure_sets
 * (e.g., strongholds, pillager_outposts) within the same session.
 *
 * Usage:
 *   /mvs debug profiler start  - Begin profiling (resets counters)
 *   /mvs debug profiler stats  - Show current stats in chat
 *   /mvs debug profiler stop   - Stop and log detailed report
 */
public class ChunkGenerationProfiler {

    // Configuration
    private static final int MIN_CHUNKS_FOR_STATS = 10; // Minimum chunks for meaningful stats

    // Profiling state
    private static volatile boolean running = false;

    // Counters (atomic for thread safety)
    private static final AtomicLong totalChunks = new AtomicLong(0);
    private static final AtomicLong totalStructureGenTimeNs = new AtomicLong(0);

    // MVS-controlled structure_sets (e.g., minecraft:villages)
    private static final AtomicLong mvsSpacingPassed = new AtomicLong(0);       // Passed spacing check
    private static final AtomicLong mvsFrequencyFailures = new AtomicLong(0);   // Failed frequency roll
    private static final AtomicLong mvsSelectionTimeNs = new AtomicLong(0);     // Time for MVS logic (biome match, weighted select)
    private static final AtomicLong mvsGenerationTimeNs = new AtomicLong(0);    // Time for tryGenerateStructure
    private static final AtomicLong mvsStructureSelections = new AtomicLong(0); // Passed frequency, attempted gen
    private static final AtomicLong mvsGenerationSuccesses = new AtomicLong(0); // Actually generated

    // Vanilla structure_sets (e.g., minecraft:strongholds, pillager_outposts)
    private static final AtomicLong vanillaPassthroughTimeNs = new AtomicLong(0);
    private static final AtomicLong vanillaPassthroughs = new AtomicLong(0);

    // Timing
    private static long sessionStartTime = System.currentTimeMillis();

    // Per-chunk timing (thread-local for concurrent chunk gen)
    private static final ThreadLocal<Long> chunkStartTime = new ThreadLocal<>();

    /**
     * Start profiling. Resets counters.
     */
    public static void start() {
        reset();
        running = true;
        MultiVillageSelector.LOGGER.info("[MVS Profiler] Started - fly around to collect data");
    }

    /**
     * Stop profiling and log final stats.
     */
    public static void stop() {
        if (running) {
            running = false;
            logStats();
            MultiVillageSelector.LOGGER.info("[MVS Profiler] Stopped");
        }
    }

    /**
     * Check if profiler is currently running.
     */
    public static boolean isRunning() {
        return running;
    }

    /**
     * Call at the START of createStructures method.
     */
    public static void startChunkTiming() {
        if (!running) return;
        chunkStartTime.set(System.nanoTime());
    }

    /**
     * Call at the END of createStructures method.
     */
    public static void endChunkTiming() {
        if (!running) return;
        Long start = chunkStartTime.get();
        if (start != null) {
            long elapsed = System.nanoTime() - start;
            totalStructureGenTimeNs.addAndGet(elapsed);
            totalChunks.incrementAndGet();
            chunkStartTime.remove();
        }
    }

    /**
     * Call when MVS passes spacing check (before frequency roll).
     */
    public static void recordMVSSpacingPassed() {
        if (!running) return;
        mvsSpacingPassed.incrementAndGet();
    }

    /**
     * Call when MVS frequency check fails.
     */
    public static void recordMVSFrequencyFailure() {
        if (!running) return;
        mvsFrequencyFailures.incrementAndGet();
    }

    /**
     * Call to record MVS selection time (biome matching, weighted random).
     * @param timeNs Time spent in MVS selection logic (nanoseconds)
     */
    public static void recordMVSSelectionTime(long timeNs) {
        if (!running) return;
        mvsSelectionTimeNs.addAndGet(timeNs);
    }

    /**
     * Call to record structure generation time (tryGenerateStructure).
     * @param timeNs Time spent generating the structure (nanoseconds)
     */
    public static void recordMVSGenerationTime(long timeNs) {
        if (!running) return;
        mvsGenerationTimeNs.addAndGet(timeNs);
    }

    /**
     * Call when MVS selects a structure (passed frequency, attempting gen).
     */
    public static void recordMVSSelection() {
        if (!running) return;
        mvsStructureSelections.incrementAndGet();
    }

    /**
     * Call when MVS passes through to vanilla behavior.
     * @param timeNs Time spent in vanilla consumer (nanoseconds)
     */
    public static void recordVanillaPassthrough(long timeNs) {
        if (!running) return;
        vanillaPassthroughTimeNs.addAndGet(timeNs);
        vanillaPassthroughs.incrementAndGet();
    }

    /**
     * Call when MVS successfully generates a structure.
     */
    public static void recordMVSGenerationSuccess() {
        if (!running) return;
        mvsGenerationSuccesses.incrementAndGet();
    }

    /**
     * Log detailed stats to console.
     */
    public static void logStats() {
        long chunks = totalChunks.get();
        if (chunks == 0) {
            MultiVillageSelector.LOGGER.info("[MVS Profiler] No chunks processed yet");
            return;
        }

        long totalTimeNs = totalStructureGenTimeNs.get();
        long selectionTimeNs = mvsSelectionTimeNs.get();
        long generationTimeNs = mvsGenerationTimeNs.get();
        long vanillaTimeNs = vanillaPassthroughTimeNs.get();
        long spacingPassed = mvsSpacingPassed.get();
        long freqFailures = mvsFrequencyFailures.get();
        long mvsSelections = mvsStructureSelections.get();
        long mvsSuccesses = mvsGenerationSuccesses.get();
        long vanillaPasses = vanillaPassthroughs.get();
        long sessionDurationMs = System.currentTimeMillis() - sessionStartTime;

        // Calculate averages (per chunk)
        double avgTotalUs = (totalTimeNs / (double) chunks) / 1000.0;

        // Calculate per-call averages
        double avgSelectionUs = mvsSelections > 0 ? (selectionTimeNs / (double) mvsSelections) / 1000.0 : 0;
        double avgGenerationUs = mvsSelections > 0 ? (generationTimeNs / (double) mvsSelections) / 1000.0 : 0;
        double avgVanillaPerCallUs = vanillaPasses > 0 ? (vanillaTimeNs / (double) vanillaPasses) / 1000.0 : 0;

        // Calculate rates
        double chunksPerSecond = chunks / (sessionDurationMs / 1000.0);
        double freqFailRate = spacingPassed > 0 ? (freqFailures * 100.0 / spacingPassed) : 0;
        double mvsSuccessRate = mvsSelections > 0 ? (mvsSuccesses * 100.0 / mvsSelections) : 0;

        MultiVillageSelector.LOGGER.info("========== MVS Performance Report ==========");
        MultiVillageSelector.LOGGER.info("  Session: {} min | MVS enabled: {}", sessionDurationMs / 60000, MVSConfig.enabled);
        MultiVillageSelector.LOGGER.info("  Chunks: {} ({}/sec)", chunks, String.format("%.1f", chunksPerSecond));
        MultiVillageSelector.LOGGER.info("  Avg structure gen: {} µs/chunk", String.format("%.1f", avgTotalUs));
        MultiVillageSelector.LOGGER.info("");
        MultiVillageSelector.LOGGER.info("  --- MVS-Controlled (e.g., villages) ---");
        MultiVillageSelector.LOGGER.info("  Spacing passed: {}", spacingPassed);
        MultiVillageSelector.LOGGER.info("  Frequency failures: {} ({})", freqFailures, String.format("%.1f%%", freqFailRate));
        MultiVillageSelector.LOGGER.info("  Selection attempts: {} | Successes: {} ({})",
            mvsSelections, mvsSuccesses, String.format("%.1f%%", mvsSuccessRate));
        MultiVillageSelector.LOGGER.info("  Avg MVS selection time: {} µs  ← MVS overhead", String.format("%.1f", avgSelectionUs));
        MultiVillageSelector.LOGGER.info("  Avg generation time: {} µs  ← Minecraft's work", String.format("%.1f", avgGenerationUs));
        MultiVillageSelector.LOGGER.info("");
        MultiVillageSelector.LOGGER.info("  --- Vanilla Structure Sets ---");
        MultiVillageSelector.LOGGER.info("  Passthrough calls: {}", vanillaPasses);
        MultiVillageSelector.LOGGER.info("  Avg time per call: {} µs", String.format("%.1f", avgVanillaPerCallUs));
        MultiVillageSelector.LOGGER.info("");
        MultiVillageSelector.LOGGER.info("  --- Comparison (MVS selection vs vanilla passthrough) ---");
        if (avgVanillaPerCallUs > 0) {
            double overhead = avgSelectionUs - avgVanillaPerCallUs;
            double overheadPercent = (overhead / avgVanillaPerCallUs) * 100;
            MultiVillageSelector.LOGGER.info("  MVS selection overhead: {} µs ({} vs vanilla)",
                String.format("%+.1f", overhead),
                String.format("%+.1f%%", overheadPercent));
        } else {
            MultiVillageSelector.LOGGER.info("  MVS selection overhead: N/A (no vanilla calls)");
        }
        MultiVillageSelector.LOGGER.info("=============================================");
    }

    /**
     * Reset all counters (for fresh comparison runs).
     */
    public static void reset() {
        totalChunks.set(0);
        totalStructureGenTimeNs.set(0);
        mvsSpacingPassed.set(0);
        mvsFrequencyFailures.set(0);
        mvsSelectionTimeNs.set(0);
        mvsGenerationTimeNs.set(0);
        mvsStructureSelections.set(0);
        mvsGenerationSuccesses.set(0);
        vanillaPassthroughTimeNs.set(0);
        vanillaPassthroughs.set(0);
        sessionStartTime = System.currentTimeMillis();
        MultiVillageSelector.LOGGER.info("[MVS Profiler] Counters reset");
    }

    /**
     * Get current stats as formatted string (for commands).
     */
    public static String getStatsString() {
        long chunks = totalChunks.get();
        long elapsedMs = System.currentTimeMillis() - sessionStartTime;

        if (chunks == 0) {
            return String.format("Elapsed: %s | No chunks processed yet", formatDuration(elapsedMs));
        }

        long mvsTimeNs = mvsSelectionTimeNs.get();
        long vanillaTimeNs = vanillaPassthroughTimeNs.get();
        long mvsSelections = mvsStructureSelections.get();
        long mvsSuccesses = mvsGenerationSuccesses.get();
        long vanillaPasses = vanillaPassthroughs.get();

        double avgMvsUs = mvsSelections > 0 ? (mvsTimeNs / (double) mvsSelections) / 1000.0 : 0;
        double avgVanillaUs = vanillaPasses > 0 ? (vanillaTimeNs / (double) vanillaPasses) / 1000.0 : 0;

        return String.format(
            "Elapsed: %s | Chunks: %d | MVS: %.0f µs/call (%d/%d success) | Vanilla: %.0f µs/call (%d calls)",
            formatDuration(elapsedMs), chunks, avgMvsUs, mvsSuccesses, mvsSelections, avgVanillaUs, vanillaPasses
        );
    }

    /**
     * Format milliseconds as human-readable duration (e.g., "2m 34s" or "45s").
     */
    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }
}
