/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces Kafka-style record retention on a data partition: it keeps the most recent {@code
 * maxRecords} records and periodically compacts older Raft log segments. Retention is driven purely
 * by the partition's own committed log — <b>not</b> by consumer progress — so a slow or absent
 * consumer can never pin the log and exhaust disk; a consumer that falls behind the retained window
 * resumes via its {@code OffsetResetPolicy}.
 *
 * <p>Rather than deleting segments directly, it takes an empty <b>marker snapshot</b> at the
 * retention bound. A data partition is a pure event log with no state machine, so the snapshot
 * carries no state — only its log index matters. Persisting it goes through Raft's normal snapshot
 * machinery, which (a) compacts the log up to that index and (b) lets the leader catch up a replica
 * that fell behind the retained window via {@code InstallSnapshot} instead of leaving it stuck with
 * no recoverable history.
 *
 * <p>This runs on <b>every</b> replica (leader and followers): each reads its own committed log
 * tail and snapshots/compacts independently. That is safe and cannot diverge — compaction only
 * removes an already-committed prefix, which Raft guarantees is identical on every replica, so
 * nodes differ only in how far back they retain, never in shared content.
 */
public final class LogRetentionCompactor extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(LogRetentionCompactor.class);
  private static final String MARKER_FILE = "retention";

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final ConstructableSnapshotStore snapshotStore;
  private final long maxRecords;
  private final Duration interval;

  public LogRetentionCompactor(
      final int partitionId,
      final RaftPartition raftPartition,
      final ConstructableSnapshotStore snapshotStore,
      final long maxRecords,
      final Duration interval) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
    this.snapshotStore = snapshotStore;
    this.maxRecords = maxRecords;
    this.interval = interval;
  }

  @Override
  public String getName() {
    return "EventBridgeLogRetention-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(interval, this::compact);
  }

  private void compact() {
    // Fetch the server fresh each cycle: it is created during raft bootstrap, after this actor is
    // constructed, and is the same instance across role changes.
    final var server = raftPartition.getServer();
    if (server == null) {
      return; // partition not bootstrapped yet
    }

    final long lastPosition = lastCommittedPosition(server);
    // Nothing committed yet, or the whole log still fits within the retained window.
    if (lastPosition < 0 || lastPosition <= maxRecords) {
      return;
    }

    // Oldest record position we must keep; everything strictly before it is eligible for deletion.
    final long retainFromPosition = lastPosition - maxRecords;
    final var entry =
        new AtomixRecordEntrySupplierImpl(server).getPreviousIndexedEntry(retainFromPosition);
    if (entry.isEmpty()) {
      return; // position not yet indexed / already compacted away
    }

    takeMarkerSnapshot(entry.get().index(), entry.get().term(), retainFromPosition, lastPosition);
  }

  /**
   * Takes and persists an empty snapshot pinned to {@code index}. Raft's snapshot listener then
   * compacts the log up to it (and exposes it for InstallSnapshot). A non-advancing bound is
   * rejected by the store (a snapshot at this or a newer index already exists) and simply skipped.
   */
  private void takeMarkerSnapshot(
      final long index, final long term, final long retainFromPosition, final long lastPosition) {
    final var transientSnapshot =
        snapshotStore.newTransientSnapshot(index, term, retainFromPosition, 0, false);
    if (transientSnapshot.isLeft()) {
      LOG.trace(
          "Partition {} — no new retention snapshot at index {}: {}",
          partitionId,
          index,
          transientSnapshot.getLeft().getMessage());
      return;
    }

    final var snapshot = transientSnapshot.get();
    snapshot
        .take(this::writeMarker)
        .onComplete(
            (taken, takeError) -> {
              if (takeError != null) {
                LOG.warn(
                    "Partition {} — failed to take retention snapshot", partitionId, takeError);
                snapshot.abort();
                return;
              }
              snapshot
                  .persist()
                  .onComplete(
                      (persisted, persistError) -> {
                        if (persistError != null) {
                          LOG.warn(
                              "Partition {} — failed to persist retention snapshot",
                              partitionId,
                              persistError);
                        } else {
                          LOG.debug(
                              "Partition {} — took retention snapshot at index {} (retaining last {} records behind position {})",
                              partitionId,
                              index,
                              maxRecords,
                              lastPosition);
                        }
                      },
                      actor);
            },
            actor);
  }

  /**
   * Writes the marker file into the snapshot directory. A snapshot with an empty directory is
   * rejected as invalid, so the single marker is what makes the (state-less) snapshot well-formed.
   */
  private void writeMarker(final Path directory) {
    try {
      Files.createDirectories(directory);
      Files.writeString(
          directory.resolve(MARKER_FILE), "event-bridge-retention", StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write retention marker snapshot", e);
    }
  }

  /**
   * Reads the highest committed record position from this replica's own Raft log, or {@code -1} if
   * the log is empty or not yet readable. Uses the committed reader, so followers see only entries
   * the cluster has agreed on.
   */
  private long lastCommittedPosition(final RaftPartitionServer server) {
    try (final var reader = server.openReader()) {
      reader.seekToLast();
      if (!reader.hasNext()) {
        return -1;
      }
      final var entry = reader.next();
      return entry.isApplicationEntry() ? entry.getApplicationEntry().highestPosition() : -1;
    } catch (final Exception e) {
      LOG.debug(
          "Partition {} — could not read last committed position for retention", partitionId, e);
      return -1;
    }
  }
}
