/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.eventbridge.broker.watermark.HighWatermark;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.scheduler.Actor;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces Kafka-style record retention on a data-partition leader: it keeps the most recent {@code
 * maxRecords} records and periodically compacts older Raft log segments. Retention is driven purely
 * by the partition's own committed position — <b>not</b> by consumer progress — so a slow or absent
 * consumer can never pin the log and exhaust disk; a consumer that falls behind the retained window
 * resumes via its {@code OffsetResetPolicy}.
 *
 * <p>Each cycle resolves the record position {@code maxRecords} behind the high watermark to a Raft
 * log index (via {@link AtomixRecordEntrySupplier}) and asks the Raft layer to compact up to it.
 * Compaction is segment-granular, so the actual deletion point is rounded down to a segment
 * boundary — it never removes a record inside the retained window.
 */
public final class LogRetentionCompactor extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(LogRetentionCompactor.class);

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final HighWatermark highWatermark;
  private final long maxRecords;
  private final Duration interval;
  private final AtomixRecordEntrySupplier entrySupplier;

  public LogRetentionCompactor(
      final int partitionId,
      final RaftPartition raftPartition,
      final HighWatermark highWatermark,
      final long maxRecords,
      final Duration interval) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
    this.highWatermark = highWatermark;
    this.maxRecords = maxRecords;
    this.interval = interval;
    entrySupplier = new AtomixRecordEntrySupplierImpl(raftPartition.getServer());
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
    final long highPosition = highWatermark.get().commitPosition();
    // Nothing committed yet, or the whole log still fits within the retained window.
    if (highPosition < 0 || highPosition <= maxRecords) {
      return;
    }

    // Oldest record position we must keep; everything strictly before it is eligible for deletion.
    final long retainFromPosition = highPosition - maxRecords;
    final var entry = entrySupplier.getPreviousIndexedEntry(retainFromPosition);
    if (entry.isEmpty()) {
      return; // position not yet indexed / already compacted away
    }

    final long compactableIndex = entry.get().index();
    raftPartition
        .getServer()
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
                    highPosition);
              }
            });
  }
}
