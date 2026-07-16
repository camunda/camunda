/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Adapts {@link ManifestStore} onto a partition's Raft {@link ConstructableSnapshotStore} (event-
 * bridge ADR 0001, decision 3): a compaction pass's commit point <em>is</em> the partition's Raft
 * snapshot. Raft then truncates the raw log up to the snapshot index on its own schedule and ships
 * the snapshot to a lagging follower via its existing InstallSnapshot machinery — nothing extra is
 * built for either, by design (see {@link ManifestStore}'s javadoc on why this seam exists).
 *
 * <h3>{@link #commit}: position &rarr; raft index/term &rarr; transient snapshot</h3>
 *
 * <p>The manifest's cleaner point {@code C} is a log <em>position</em>; a Raft snapshot is pinned
 * to an <em>index and term</em>. {@link #commit} resolves {@code C} via {@link
 * AtomixRecordEntrySupplier#getPreviousIndexedEntry}, the same resolution {@code
 * LogRetentionCompactor} and {@code StateControllerImpl} use, then opens a transient snapshot at
 * that index/term, hardlinks the pass's new clean segments (already durably written by {@link
 * CleanSegmentWriter}) into the snapshot's own directory, writes the manifest there via {@link
 * FileManifestStore}, and persists. A position not yet resolvable to an index (a very young or
 * empty log) fails the commit with an exception rather than silently doing nothing: {@link
 * CompactionPass} propagates it up, {@code LogCleaner} logs and retries next tick, and — exactly as
 * a crash would — the previously committed manifest remains authoritative and the orphaned new
 * segments are swept by {@link TrashQueue#recoverOrphans()} on the next attempt.
 *
 * <h3>Bridging a synchronous contract onto an asynchronous store</h3>
 *
 * <p>{@link ManifestStore#commit} is synchronous by contract ("after a successful return, {@link
 * #latest()} reflects this manifest"), but {@code take}/{@code persist} are actor-future based.
 * This adapter blocks the calling thread (the single cleaner actor, via {@link
 * ActorFuture#join(long, TimeUnit)} with a bounded timeout) until both complete. This is safe here
 * specifically because the cleaner actor has no other concurrent work to stall and a pass runs at
 * most once per {@code passInterval} (seconds-to-minutes); it is not a pattern to copy onto a
 * latency-sensitive actor.
 *
 * <h3>{@link #latest} also installs: the "receive path needs no change" argument</h3>
 *
 * <p>A persisted snapshot's manifest and segments live in the <em>snapshot store's own</em>
 * directory, not in the compaction working directory {@link CompactionPass} and {@link TrashQueue}
 * read from. Whenever the newest persisted snapshot's content is not yet mirrored into the working
 * directory — on first start, after a restart onto fresh/relocated storage, or once a lagging
 * follower's Raft layer has just finished an InstallSnapshot — {@link #latest} hardlinks (falling
 * back to a copy across devices) every referenced segment into the working directory before
 * returning the manifest. Every consumer of a committed manifest ({@code CompactionPass} at the top
 * of each pass, {@code TrashQueue} on every drain and {@code recoverOrphans}) calls {@link #latest}
 * fresh, and the {@code LogCleaner} actor calls it once per tick regardless of why a new snapshot
 * appeared — so no separate {@code PersistedSnapshotListener} for the InstallSnapshot receive path
 * is needed: the next scheduled tick after <em>any</em> snapshot arrival (locally taken or
 * received) self-heals the working directory through this same call. Once installed, a segment is
 * left alone (existence-checked, not re-copied) since it is immutable once written.
 *
 * <p>Threading: driven by the single cleaner actor; not thread-safe.
 */
public final class SnapshotManifestStore implements ManifestStore {

  private static final Duration COMMIT_TIMEOUT = Duration.ofSeconds(30);

  private final int partitionId;
  private final Path compactionDirectory;
  private final ConstructableSnapshotStore snapshotStore;
  private final AtomixRecordEntrySupplier entrySupplier;
  private final Duration commitTimeout;

  /**
   * @param partitionId the data partition this store serves (for error messages)
   * @param compactionDirectory the cleaner's working directory; segments referenced by the latest
   *     persisted snapshot are installed here on demand
   * @param snapshotStore the partition's Raft snapshot store
   * @param entrySupplier resolves a log position to the raft index/term of the previous indexed
   *     entry (e.g. {@code AtomixRecordEntrySupplierImpl})
   */
  public SnapshotManifestStore(
      final int partitionId,
      final Path compactionDirectory,
      final ConstructableSnapshotStore snapshotStore,
      final AtomixRecordEntrySupplier entrySupplier) {
    this(partitionId, compactionDirectory, snapshotStore, entrySupplier, COMMIT_TIMEOUT);
  }

  /** Test seam: a shorter commit timeout than the {@link #COMMIT_TIMEOUT} production default. */
  SnapshotManifestStore(
      final int partitionId,
      final Path compactionDirectory,
      final ConstructableSnapshotStore snapshotStore,
      final AtomixRecordEntrySupplier entrySupplier,
      final Duration commitTimeout) {
    this.partitionId = partitionId;
    this.compactionDirectory = compactionDirectory;
    this.snapshotStore = snapshotStore;
    this.entrySupplier = entrySupplier;
    this.commitTimeout = commitTimeout;
  }

  @Override
  public void commit(final CompactionManifest manifest, final List<Path> newFiles) {
    final long cleanerPoint = manifest.cleanerPoint();
    final var indexedEntry = entrySupplier.getPreviousIndexedEntry(cleanerPoint);
    if (indexedEntry.isEmpty()) {
      throw new IllegalStateException(
          "Partition "
              + partitionId
              + " — cannot resolve a raft index/term for cleaner point "
              + cleanerPoint
              + "; leaving the previously committed manifest authoritative and retrying next"
              + " pass");
    }
    final var entry = indexedEntry.get();

    final var transientSnapshot =
        snapshotStore.newTransientSnapshot(entry.index(), entry.term(), cleanerPoint, 0, false);
    if (transientSnapshot.isLeft()) {
      // A snapshot at this or a newer index already exists (a non-advancing bound) — latest()
      // already reflects it; nothing further for this pass to do.
      return;
    }
    final var snapshot = transientSnapshot.get();

    final var takeFuture = snapshot.take(dir -> writeSnapshotContent(dir, manifest, newFiles));
    try {
      takeFuture.join(commitTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      snapshot.abort();
      throw new IllegalStateException(
          "Partition "
              + partitionId
              + " — failed to take compaction snapshot at index "
              + entry.index(),
          e);
    }

    try {
      snapshot.persist().join(commitTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Partition "
              + partitionId
              + " — failed to persist compaction snapshot at index "
              + entry.index(),
          e);
    }
  }

  /**
   * Writes the snapshot's content: hardlinks every segment this pass wrote (already durable in the
   * compaction directory) into the snapshot directory, then writes the manifest there. Every
   * element of {@code manifest.segments()} corresponds 1:1 to an element of {@code newFiles} — a
   * pass never carries a segment forward without rewriting it (see {@link CompactionPass}).
   */
  private void writeSnapshotContent(
      final Path snapshotDir, final CompactionManifest manifest, final List<Path> newFiles) {
    for (final Path source : newFiles) {
      final Path target = snapshotDir.resolve(source.getFileName());
      try {
        Files.createLink(target, source);
      } catch (final IOException e) {
        throw new UncheckedIOException(
            "Failed to hardlink clean segment "
                + source
                + " into snapshot directory "
                + snapshotDir,
            e);
      }
    }
    // The manifest write is the FileManifestStore commit point (temp file, fsync, atomic rename,
    // dir fsync); newFiles are already fsynced by CleanSegmentWriter, and a hardlink shares the
    // source inode, so no further fsync of the segment bytes is needed here.
    new FileManifestStore(snapshotDir).commit(manifest, List.of());
  }

  @Override
  public Optional<CompactionManifest> latest() {
    return snapshotStore.getLatestSnapshot().map(this::installAndRead);
  }

  private CompactionManifest installAndRead(final PersistedSnapshot snapshot) {
    final Path snapshotDir = snapshot.getPath();
    final CompactionManifest manifest =
        new FileManifestStore(snapshotDir)
            .latest()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Partition "
                            + partitionId
                            + " — persisted snapshot at "
                            + snapshotDir
                            + " carries no compaction manifest"));
    for (final CleanSegment segment : manifest.segments()) {
      installIfMissing(snapshotDir, segment);
    }
    return manifest;
  }

  /**
   * Ensures {@code segment} exists in the compaction working directory, installing it from the
   * snapshot directory if not — the case on first start, after a restart onto fresh/relocated
   * storage, or right after Raft's InstallSnapshot lands new content on a lagging follower. A
   * segment already present locally (the common case: this replica wrote it itself) is left alone.
   */
  private void installIfMissing(final Path snapshotDir, final CleanSegment segment) {
    final Path local = compactionDirectory.resolve(segment.fileName());
    if (Files.exists(local)) {
      return;
    }
    final Path source = snapshotDir.resolve(segment.fileName());
    try {
      Files.createLink(local, source);
    } catch (final IOException hardlinkError) {
      try {
        Files.copy(source, local);
      } catch (final IOException copyError) {
        throw new UncheckedIOException(
            "Failed to install clean segment "
                + segment.fileName()
                + " from persisted snapshot "
                + snapshotDir
                + " into compaction directory "
                + compactionDirectory,
            copyError);
      }
    }
  }
}
