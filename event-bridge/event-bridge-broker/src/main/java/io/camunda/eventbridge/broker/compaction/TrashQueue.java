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
import java.nio.file.Path;
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
 * is <em>never</em> unlinked immediately; it is enqueued after the pass that superseded it has
 * committed, and only actually unlinked once every one of these holds:
 *
 * <ol>
 *   <li>it is not referenced by the latest committed manifest;
 *   <li>its replacement is durable — guaranteed by ordering: files are enqueued only after {@link
 *       ManifestStore#commit} returns, so the manifest that supersedes them is already durable;
 *   <li>an external predicate permits it — the default is always-true; step 5 plugs the "Raft
 *       snapshot has advanced past this file" condition in here;
 *   <li>no reader holds a lease on it (see {@link ReaderLeaseRegistry}).
 * </ol>
 *
 * <p>The queue drains on the cleaner actor. Files that are not yet eligible stay queued and are
 * retried on the next drain. A crash before a drain loses the in-memory queue, but the files are
 * still on disk and not referenced by the committed manifest, so {@link #recoverOrphans()}
 * re-derives them from the manifest/directory diff on restart — the queue is reconstructible, never
 * a source of truth.
 *
 * <p>Threading: enqueue, drain and recovery run on the single cleaner actor; the lease counts it
 * consults are independently thread-safe.
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
   * @param leases the reader-lease registry consulted before unlinking
   * @param latestManifest supplies the currently-committed manifest (files it references are never
   *     unlinked)
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
   * Attempts to unlink every queued file whose deletion conditions all hold; ineligible files stay
   * queued for a later drain.
   *
   * @return the number of files actually unlinked
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
      if (!isEligible(file, referenced, externalOk)) {
        continue;
      }
      try {
        Files.deleteIfExists(file);
        iterator.remove();
        deleted++;
      } catch (final IOException e) {
        LOG.warn("Failed to unlink superseded clean segment {}, will retry", file, e);
      }
    }
    if (deleted > 0) {
      DurableFiles.fsyncDir(directory);
    }
    return deleted;
  }

  /**
   * Re-derives the trash set from the directory/manifest diff, the recovery route after a crash
   * lost the in-memory queue: any finalized clean segment on disk not referenced by the latest
   * committed manifest is an orphan (from a pass that crashed before or after commit) and is
   * enqueued; any staging temp file is unconditional garbage from an aborted write and is deleted
   * outright.
   */
  public void recoverOrphans() {
    final Set<String> referenced = referencedFileNames();
    try (final Stream<Path> entries = Files.list(directory)) {
      entries.forEach(
          path -> {
            final String name = path.getFileName().toString();
            if (CleanSegmentFiles.isTmp(name)) {
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

  private boolean isEligible(
      final Path file, final Set<String> referenced, final boolean externalOk) {
    final String name = file.getFileName().toString();
    if (referenced.contains(name)) {
      return false; // referenced by the latest committed manifest
    }
    if (!externalOk) {
      return false; // external predicate (e.g. Raft snapshot not advanced yet)
    }
    return leases.leaseCount(file) == 0; // no reader holds it
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
      LOG.warn("Failed to delete stale compaction temp file {}", path, e);
    }
  }
}
