/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.broker.compaction.KeyHash.Hash128;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * The deterministic core of the cleaner — a stateless idempotent function of the durable log and
 * the committed manifest (ADR 0001, consequences). One {@link #runOnce()} folds the raw log up to a
 * freshly-picked cleaner point into a new clean set and commits it. It has no mutable state of its
 * own between passes: everything it needs it reads from the injected seams, so it is directly
 * unit-testable without a running partition, and a crash simply means the next call reruns from the
 * last committed manifest.
 *
 * <h3>The pass, step by step</h3>
 *
 * <ol>
 *   <li><b>Recover.</b> {@link TrashQueue#recoverOrphans()} sweeps segments/temp files left by a
 *       pass that crashed before committing.
 *   <li><b>Pick C.</b> {@code C = lastCommittedPosition − minLagRecords}. This is computed only
 *       from the committed log, so every replica picks the same C for the same log state — the
 *       basis of replica-local determinism. (See the position-based-C note below.)
 *   <li><b>Build map.</b> One ascending scan of the dirty range {@code (C_prev, C]} fills the
 *       {@link KeyOffsetMap} with the latest position per key. On overflow the effective C is
 *       lowered to the highest fully-absorbed position and a later pass finishes the range.
 *   <li><b>Sweep.</b> The previous clean set then the dirty range are streamed in position order; a
 *       record is kept only if it is the latest for its key (or key-less), with tombstone two-touch
 *       grace applied. Positions are preserved verbatim — dropped records leave gaps.
 *   <li><b>Commit.</b> The new manifest + segments are published atomically through {@link
 *       ManifestStore}. This is the only durability point.
 *   <li><b>Tidy.</b> Superseded segment files are handed to the {@link TrashQueue} and a drain is
 *       attempted.
 * </ol>
 *
 * <h3>Position-based cleaner point (deviation flag)</h3>
 *
 * <p>ADR 0001 decision 6.1 phrases C as "the highest sealed journal-segment boundary". This
 * implementation instead uses a position: {@code lastCommittedPosition − minLagRecords}. A position
 * is equally deterministic across replicas (it derives only from the replicated committed log) and
 * decouples this library from journal-segment internals, which keeps it testable without a journal.
 * Flagged so the ADR can be amended.
 *
 * <h3>Un-keyed records on a compacted topic</h3>
 *
 * <p>Batches without keys are legal on the wire today (publish validation that rejects them on a
 * {@code COMPACT} topic is step 5). This pass copies key-less records forward verbatim and never
 * treats them as compactable — they have no key to coalesce on. Once step 5 lands, a compacted
 * topic will contain only keyed records; until then this is the faithful pre-validation behavior.
 *
 * <h3>Determinism and the tombstone-grace caveat</h3>
 *
 * <p>Given identical inputs and identical {@link InstantSource} readings, two runs produce
 * byte-identical segments and manifest. The one wall-clock input is the tombstone grace stamp: it
 * is read from {@link InstantSource} (never the system clock directly, never {@code Thread.sleep}).
 * Note that grace is therefore wall-clock <em>per replica</em> — see {@link LogCleaner} for the
 * cross-replica implications.
 *
 * <p>Threading: a pass instance is owned and driven by the single cleaner actor; {@link #runOnce()}
 * is not re-entrant.
 */
public final class CompactionPass {

  private final Path directory;
  private final CompactionConfig config;
  private final ManifestStore manifestStore;
  private final DirtyLogReader dirtyLog;
  private final TrashQueue trashQueue;
  private final InstantSource clock;
  private final LongSupplier lastCommittedPosition;
  private final Fault fault;

  /**
   * @param directory the compaction directory
   * @param config the cleaner tunables
   * @param manifestStore the durability seam (the commit point)
   * @param dirtyLog the raw-log read seam
   * @param trashQueue the deferred-deletion queue for superseded files
   * @param clock the time source for tombstone grace stamps (inject a fixed source in tests)
   * @param lastCommittedPosition supplies the partition's highest committed position
   * @param fault a crash-injection hook for tests; use {@link Fault#none()} in production
   */
  public CompactionPass(
      final Path directory,
      final CompactionConfig config,
      final ManifestStore manifestStore,
      final DirtyLogReader dirtyLog,
      final TrashQueue trashQueue,
      final InstantSource clock,
      final LongSupplier lastCommittedPosition,
      final Fault fault) {
    this.directory = directory;
    this.config = config;
    this.manifestStore = manifestStore;
    this.dirtyLog = dirtyLog;
    this.trashQueue = trashQueue;
    this.clock = clock;
    this.lastCommittedPosition = lastCommittedPosition;
    this.fault = fault;
  }

  /**
   * Runs a single cleaner pass.
   *
   * @return a summary of what the pass did
   */
  public PassResult runOnce() {
    trashQueue.recoverOrphans();

    final CompactionManifest prev = manifestStore.latest().orElse(CompactionManifest.empty());
    final long cPrev = prev.cleanerPoint();

    final long targetC = lastCommittedPosition.getAsLong() - config.minLagRecords();
    if (targetC <= cPrev) {
      trashQueue.drain();
      return PassResult.nothingToDo(cPrev);
    }

    // -- Phase: build the latest-per-key map over the dirty range (cPrev, targetC] --
    final var map = new KeyOffsetMap(config.keyMapCapacity());
    dirtyLog.read(
        cPrev,
        targetC,
        record -> {
          if (record.hasKey()) {
            return map.recordLatest(KeyHash.hash(record.key()), record.position());
          }
          return true;
        });
    final long effectiveC = map.overflowed() ? map.highestFitPosition() : targetC;
    fault.onPhase(Phase.MAP_BUILT);
    if (effectiveC <= cPrev) {
      // Overflowed before absorbing anything past the previous point; cannot make progress this
      // pass (keyMapCapacity is too small for even one step). Leave the committed state
      // authoritative.
      trashQueue.drain();
      return PassResult.overflowStalled(cPrev);
    }

    // -- Phase: sweep previous clean set + dirty range in position order --
    final long now = clock.millis();
    final var writer = new CleanSegmentWriter(directory, effectiveC, now, config.maxSegmentBytes());
    final var newStamps = new HashMap<Long, Long>();
    final long graceMillis = config.graceWindow().toMillis();

    for (final CleanSegment segment : prev.segments()) {
      final var reader = new CleanSegmentReader(directory.resolve(segment.fileName()));
      while (reader.hasNext()) {
        decide(reader.next(), map, prev, now, graceMillis, writer, newStamps);
      }
    }
    fault.onPhase(Phase.MID_SWEEP);
    dirtyLog.read(
        cPrev,
        effectiveC,
        record -> {
          decide(record, map, prev, now, graceMillis, writer, newStamps);
          return true;
        });

    final List<CleanSegment> segments = writer.finish();

    // -- Phase: commit the new clean set atomically --
    final var next =
        new CompactionManifest(CompactionManifest.VERSION_1, effectiveC, segments, newStamps);
    final List<Path> newPaths =
        segments.stream().map(s -> directory.resolve(s.fileName())).toList();
    fault.onPhase(Phase.BEFORE_COMMIT);
    manifestStore.commit(next, newPaths);
    fault.onPhase(Phase.AFTER_COMMIT);

    // -- Phase: hand superseded files to the trash queue and drain --
    final Set<String> keptNames =
        segments.stream().map(CleanSegment::fileName).collect(Collectors.toSet());
    for (final CleanSegment old : prev.segments()) {
      if (!keptNames.contains(old.fileName())) {
        trashQueue.enqueue(directory.resolve(old.fileName()));
      }
    }
    trashQueue.drain();

    return PassResult.committed(next, map.overflowed());
  }

  private void decide(
      final CompactionRecord record,
      final KeyOffsetMap map,
      final CompactionManifest prev,
      final long now,
      final long graceMillis,
      final CleanSegmentWriter writer,
      final Map<Long, Long> newStamps) {
    if (!record.hasKey()) {
      // Un-keyed record: copy forward verbatim, never compactable.
      writer.append(record);
      return;
    }

    final Hash128 hash = KeyHash.hash(record.key());
    final long latest = map.latest(hash);
    if (latest != KeyOffsetMap.NO_ENTRY && latest != record.position()) {
      // A newer record for this key exists in the dirty range — this one is superseded. If it was a
      // stamped tombstone, dropping it here (and not carrying its stamp) is exactly the
      // resurrection
      // path: the newer record wins.
      return;
    }

    if (record.isTombstone()) {
      final long stamp = prev.tombstoneStamp(record.position());
      if (stamp == CompactionManifest.NO_STAMP) {
        // First time this tombstone enters the clean set: keep it, stamp it now.
        writer.append(record);
        newStamps.put(record.position(), now);
      } else if (now - stamp > graceMillis) {
        // Two-touch: grace has elapsed on a later pass — drop it.
        return;
      } else {
        // Still within grace: keep it, preserving the original stamp so grace actually elapses.
        writer.append(record);
        newStamps.put(record.position(), stamp);
      }
      return;
    }

    writer.append(record);
  }

  /** The phase boundaries at which a {@link Fault} may simulate a crash. */
  public enum Phase {
    /** After the key-offset map is built, before any segment is written. */
    MAP_BUILT,
    /** During the sweep, after the previous clean set has been carried forward. */
    MID_SWEEP,
    /** After all segments are durably written, before the manifest commit. */
    BEFORE_COMMIT,
    /** After the manifest commit, before superseded files are trashed. */
    AFTER_COMMIT
  }

  /**
   * A crash-injection hook. Production passes {@link #none()}; tests throw at a chosen {@link
   * Phase} to assert that a rerun from the committed state converges to the same result.
   */
  @FunctionalInterface
  public interface Fault {
    /**
     * Invoked at each phase boundary.
     *
     * @param phase the boundary reached
     */
    void onPhase(Phase phase);

    /** Returns a no-op fault. */
    static Fault none() {
      return phase -> {};
    }
  }
}
