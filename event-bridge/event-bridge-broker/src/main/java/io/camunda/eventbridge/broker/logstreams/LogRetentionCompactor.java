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
 * <p>This runs on <b>every</b> replica (leader and followers), not just the leader. Compaction is a
 * purely local operation that deletes an already-committed log prefix; since Raft guarantees the
 * committed prefix is identical on every replica, each node trimming its own prefix is safe and
 * never causes divergence — replicas may differ only in how far back they retain, never in the
 * content they share. Each node reads its own committed log tail to decide the bound, so followers
 * (which have no leader-side high-watermark) compact independently and reclaim disk without waiting
 * for promotion.
 */
public final class LogRetentionCompactor extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(LogRetentionCompactor.class);

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final long maxRecords;
  private final Duration interval;

  public LogRetentionCompactor(
      final int partitionId,
      final RaftPartition raftPartition,
      final long maxRecords,
      final Duration interval) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
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

    final long compactableIndex = entry.get().index();
    server
        .compactUpTo(compactableIndex)
        .whenComplete(
            (deleted, error) -> {
              if (error != null) {
                LOG.warn(
                    "Partition {} — retention compaction up to index {} failed",
                    partitionId,
                    compactableIndex,
                    error);
              } else if (Boolean.TRUE.equals(deleted)) {
                LOG.debug(
                    "Partition {} — compacted log up to index {} (retaining last {} records behind position {})",
                    partitionId,
                    compactableIndex,
                    maxRecords,
                    lastPosition);
              }
            });
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
