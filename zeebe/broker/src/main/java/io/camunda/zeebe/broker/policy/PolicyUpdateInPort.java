/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.policy;

import io.atomix.raft.RaftServer.Role;
import io.camunda.security.api.model.policy.PolicyChangeSet;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for policy updates in the broker. The change set is split into chunks, and the chunks
 * are written as {@code APPEND_CHUNK} commands to the log of the coordinator partition. The update
 * is rejected unless this broker leads the coordinator partition at the time of the call.
 */
public final class PolicyUpdateInPort {

  /** The partition that coordinates policy updates. */
  public static final int COORDINATOR_PARTITION_ID = 1;

  private final PolicyChangeSetSplitter splitter;
  private final Supplier<@Nullable PartitionManager> partitionManager;

  /**
   * @param partitionManager supplies the partition manager of this broker, or {@code null} before
   *     the broker has started
   */
  public PolicyUpdateInPort(
      final PolicyChangeSetSplitter splitter,
      final Supplier<@Nullable PartitionManager> partitionManager) {
    this.splitter = splitter;
    this.partitionManager = partitionManager;
  }

  /** Whether this broker currently leads the coordinator partition. */
  public boolean isCoordinatorLeader() {
    return coordinatorRaftLeader();
  }

  /**
   * Splits the change set and writes its chunks to the coordinator partition.
   *
   * @return the number of chunks written
   * @throws IllegalStateException if this broker does not lead the coordinator partition, or a
   *     chunk cannot be written
   */
  public int apply(final String envelopeId, final PolicyChangeSet changeSet) {
    final var writer = coordinatorWriter(envelopeId);
    final var chunks = splitter.split(envelopeId, changeSet);
    for (final var chunk : chunks) {
      final var metadata =
          new RecordMetadata()
              .recordType(RecordType.COMMAND)
              .valueType(ValueType.POLICY_UPDATE)
              .intent(PolicyUpdateIntent.APPEND_CHUNK);
      final var result =
          writer.tryWrite(
              WriteContext.userCommand(PolicyUpdateIntent.APPEND_CHUNK),
              LogAppendEntry.of(metadata, chunk));
      if (result.isLeft()) {
        throw new IllegalStateException(
            "Expected to write chunk %d of policy update '%s', but the write failed: %s"
                .formatted(chunk.getChunkIndex(), envelopeId, result.getLeft()));
      }
    }
    return chunks.size();
  }

  private LogStreamWriter coordinatorWriter(final String envelopeId) {
    final var manager = partitionManager.get();
    if (manager == null || !coordinatorRaftLeader()) {
      throw new IllegalStateException(
          "Expected this broker to lead partition %d to apply policy update '%s', but it does not"
              .formatted(COORDINATOR_PARTITION_ID, envelopeId));
    }
    final ZeebePartition partition =
        manager.getZeebePartitions().stream()
            .filter(zeebePartition -> zeebePartition.getPartitionId() == COORDINATOR_PARTITION_ID)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expected partition %d to exist to apply policy update '%s'"
                            .formatted(COORDINATOR_PARTITION_ID, envelopeId)));
    final var logStream = partition.getLogStream();
    if (logStream == null) {
      throw new IllegalStateException(
          "Expected partition %d to have a log stream to apply policy update '%s'"
              .formatted(COORDINATOR_PARTITION_ID, envelopeId));
    }
    return logStream.newLogStreamWriter();
  }

  private boolean coordinatorRaftLeader() {
    final var manager = partitionManager.get();
    if (manager == null) {
      return false;
    }
    final var raftPartition = manager.getRaftPartition(COORDINATOR_PARTITION_ID);
    return raftPartition != null && raftPartition.getRole() == Role.LEADER;
  }
}
