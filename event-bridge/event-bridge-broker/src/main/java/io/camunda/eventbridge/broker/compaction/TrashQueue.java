/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deferred, refcount-guarded deletion of superseded clean segments (ADR 0001, decision 8). A file
 * is <em>never</em> unlinked under its original name; deletion follows the journal's two-step
 * pattern:
 *
 * <ol>
 *   <li><b>Condemn.</b> Once a queued file is not referenced by the latest committed manifest and
 *       the external predicate permits (default always-true; step 5 plugs the "Raft snapshot has
 *       advanced" condition here), it is renamed to a {@code *-deleted} marker. From this moment no
 *       new lease can be acquired on it — {@link ReaderLeaseRegistry#acquire} opens the original
 *       name, fails, and signals retry.
 *   <li><b>Unlink, deferred behind the refcount.</b> After the rename, the lease count is
 *       re-checked atomically ({@link ReaderLeaseRegistry}): zero means the marker is unlinked
 *       immediately; otherwise the last {@code release()} performs the unlink. Readers holding a
 *       lease keep reading through their already-open channel regardless (POSIX file semantics) —
 *       the same guarantee that protects the zero-copy fetch path's in-flight responses.
 * </ol>
 *
 * <p>Replacement durability is guaranteed by ordering: files are enqueued only after {@link
 * ManifestStore#commit} returns, so the manifest that supersedes them is already durable.
 *
 * <p>The queue drains on the cleaner actor. Files that are not yet eligible stay queued and are
 * retried on the next drain. A crash loses the in-memory queue, but every on-disk leftover is
 * recognizable by name: {@link #recoverOrphans()} re-derives the queue from the manifest/directory
 * diff (orphan segments re-enqueued) and deletes unconditional garbage outright — staging temp
 * files, stale {@code *-deleted} markers, and a torn {@code manifest.tmp} from a crashed manifest
 * write. The queue is reconstructible state, never a source of truth.
 *
 * <p>Threading: enqueue, drain and recovery run on the single cleaner actor; the condemn/release
 * interlock with concurrent readers is handled atomically by {@link ReaderLeaseRegistry}. Deleting
 * a stale marker while a (pre-crash-era impossible, but concurrent) lease still reads it is safe:
 * the lease's open descriptor outlives the unlink.
 */
public final class TrashQueue {

  private static final Logger LOG = LoggerFactory.getLogger(TrashQueue.class);

  private final Path directory;
  private final ReaderLeaseRegistry leases;
  private final Supplier<Optional<CompactionManifest>> latestManifest;
  private final BooleanSupplier externalPredicate;
  private final Set<Path> pending = new LinkedHashSet<>();

  /**
   * @param directory the compaction directory
   * @param leases the reader-lease registry the condemn/unlink interlock runs through
   * @param latestManifest supplies the currently-committed manifest (files it references are never
   *     condemned)
   * @param externalPredicate an extra gate (default {@code () -> true}); step 5 supplies the Raft
   *     snapshot-advanced condition
   */
  public TrashQueue(
      final Path directory,
      final ReaderLeaseRegistry leases,
      final Supplier<Optional<CompactionManifest>> latestManifest,
      final BooleanSupplier externalPredicate) {
    this.directory = directory;
    this.leases = leases;
    this.latestManifest = latestManifest;
    this.externalPredicate = externalPredicate;
  }

  /**
   * Enqueues a superseded segment file for deferred deletion. Must be called only after the
   * manifest that supersedes it has been committed.
   *
   * @param file the file to eventually unlink
   */
  public void enqueue(final Path file) {
    pending.add(file);
  }

  /**
   * Condemns every queued file whose deletion conditions hold (not referenced by the latest
   * committed manifest, external predicate true) and unlinks each marker whose lease count is zero;
   * markers with outstanding leases are unlinked by the last release instead. Ineligible files stay
   * queued for a later drain.
   *
   * @return the number of files whose marker was unlinked immediately during this drain
   */
  public int drain() {
    if (pending.isEmpty()) {
      return 0;
    }
    final Set<String> referenced = referencedFileNames();
    final boolean externalOk = externalPredicate.getAsBoolean();
    int deleted = 0;
    final var iterator = pending.iterator();
    while (iterator.hasNext()) {
      final Path file = iterator.next();
      final String name = file.getFileName().toString();
      if (referenced.contains(name) || !externalOk) {
        continue; // not eligible yet; retry on a later drain
      }
      final Path condemnedPath = directory.resolve(CleanSegmentFiles.condemnedName(name));
      try {
        Files.move(file, condemnedPath, StandardCopyOption.REPLACE_EXISTING);
      } catch (final NoSuchFileException e) {
        iterator.remove(); // already gone (e.g. recovered marker swept earlier)
        continue;
      } catch (final IOException e) {
        LOG.warn("Failed to condemn superseded clean segment {}, will retry", file, e);
        continue;
      }
      // Condemned: ownership of the marker passes to the lease interlock. The atomic count
      // re-check either unlinks now or defers to the last release.
      if (leases.condemn(file, condemnedPath)) {
        deleted++;
      }
      iterator.remove();
    }
    if (deleted > 0) {
      DurableFiles.fsyncDir(directory);
    }
    return deleted;
  }

  /**
   * Re-derives the trash set from the directory/manifest diff — the recovery route after a crash
   * lost the in-memory queue: any finalized clean segment on disk not referenced by the latest
   * committed manifest is an orphan (from a pass that crashed before or after commit) and is
   * enqueued; staging temp files, stale {@code *-deleted} markers, and a torn {@code manifest.tmp}
   * are unconditional garbage and are deleted outright.
   */
  public void recoverOrphans() {
    final Set<String> referenced = referencedFileNames();
    try (final Stream<Path> entries = Files.list(directory)) {
      entries.forEach(
          path -> {
            final String name = path.getFileName().toString();
            if (CleanSegmentFiles.isTmp(name)
                || CleanSegmentFiles.isCondemned(name)
                || FileManifestStore.MANIFEST_TMP.equals(name)) {
              deleteQuietly(path);
            } else if (CleanSegmentFiles.isSegment(name) && !referenced.contains(name)) {
              pending.add(path);
            }
          });
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to scan compaction directory for orphans", e);
    }
  }

  /** Returns the number of files currently queued (for observability and tests). */
  public int pendingCount() {
    return pending.size();
  }

  private Set<String> referencedFileNames() {
    final Set<String> names = new LinkedHashSet<>();
    latestManifest
        .get()
        .ifPresent(manifest -> manifest.segments().forEach(s -> names.add(s.fileName())));
    return names;
  }

  private void deleteQuietly(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException e) {
      LOG.warn("Failed to delete stale compaction file {}", path, e);
    }
  }
}
