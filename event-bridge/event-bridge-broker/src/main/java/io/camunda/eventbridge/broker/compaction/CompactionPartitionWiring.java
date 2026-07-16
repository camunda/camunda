/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.messaging.stream.EventStreamReader;
import java.nio.file.Path;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Assembles the per-replica runtime for a {@code COMPACT}-policy data partition: the {@link
 * ReaderLeaseRegistry} and {@link ManifestStore} the fetch path shares with the cleaner, the {@link
 * TrashQueue} deferred-deletion seam, and the {@link LogCleaner} actor itself. This is the COMPACT
 * counterpart of {@code LogRetentionCompactor} — the two are mutually exclusive by topic cleanup
 * policy (event-bridge ADR 0001) — and, like it, runs unconditionally on every replica (leader and
 * follower): each node cleans its own committed log prefix independently, with no cross-replica
 * coordination.
 *
 * <p>Threading: a pure factory; the returned {@link LogCleaner} must still be submitted to the
 * actor scheduler by the caller, exactly like {@code LogRetentionCompactor}.
 */
public final class CompactionPartitionWiring {

  private CompactionPartitionWiring() {}

  /**
   * Builds the compaction runtime for one replica of a {@code COMPACT} partition.
   *
   * @param partitionId the data partition this cleaner serves
   * @param compactionDirectory the directory the manifest and clean segments live in
   * @param manifestStore the durability seam for the cleaner's commit point
   * @param dirtyLogReaderSupplier supplies a freshly-opened {@link EventStreamReader} over the
   *     partition's raw log storage for each cleaner pass; the reader is closed after every read
   * @param lastCommittedPosition supplies the partition's highest committed position
   * @param config the cleaner tunables
   * @return the assembled runtime, ready for the caller to submit {@link
   *     CompactionRuntime#cleaner()} to the actor scheduler
   */
  public static CompactionRuntime build(
      final int partitionId,
      final Path compactionDirectory,
      final ManifestStore manifestStore,
      final Supplier<EventStreamReader> dirtyLogReaderSupplier,
      final LongSupplier lastCommittedPosition,
      final CompactionConfig config) {
    final var leaseRegistry = new ReaderLeaseRegistry();
    final var trashQueue =
        new TrashQueue(compactionDirectory, leaseRegistry, manifestStore::latest, () -> true);
    final var dirtyLogReader = new EventStreamDirtyLogReader(dirtyLogReaderSupplier);
    final var pass =
        new CompactionPass(
            compactionDirectory,
            config,
            manifestStore,
            dirtyLogReader,
            trashQueue,
            lastCommittedPosition,
            CompactionPass.Fault.none());
    final var cleaner = new LogCleaner(partitionId, config, pass);
    return new CompactionRuntime(cleaner, manifestStore, leaseRegistry, compactionDirectory);
  }

  /**
   * The assembled compaction runtime for one partition replica.
   *
   * @param cleaner the actor to submit to the scheduler
   * @param manifestStore the committed-manifest seam, shared with the fetch path
   * @param leaseRegistry the reader-lease registry, shared with the fetch path so a held lease
   *     always keeps a clean segment readable regardless of the cleaner's deferred deletion
   * @param compactionDirectory the directory the manifest and clean segments live in
   */
  public record CompactionRuntime(
      LogCleaner cleaner,
      ManifestStore manifestStore,
      ReaderLeaseRegistry leaseRegistry,
      Path compactionDirectory) {}
}
