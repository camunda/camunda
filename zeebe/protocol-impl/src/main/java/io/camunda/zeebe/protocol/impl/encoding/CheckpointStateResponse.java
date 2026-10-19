/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.management.CheckpointStateResponseDecoder;
import io.camunda.zeebe.protocol.management.CheckpointStateResponseEncoder;
import io.camunda.zeebe.protocol.management.CheckpointStateResponseEncoder.BackupStatesEncoder;
import io.camunda.zeebe.protocol.management.CheckpointStateResponseEncoder.CheckpointStatesEncoder;
import io.camunda.zeebe.protocol.management.CheckpointStateResponseEncoder.LogArchiveStatesEncoder;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.HashSet;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class CheckpointStateResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final CheckpointStateResponseEncoder bodyEncoder = new CheckpointStateResponseEncoder();
  private final CheckpointStateResponseDecoder bodyDecoder = new CheckpointStateResponseDecoder();

  private Set<PartitionCheckpointState> checkpointStates = new HashSet<>();
  private Set<PartitionCheckpointState> backupStates = new HashSet<>();
  private Set<PartitionLogArchiveState> logArchiveStates = new HashSet<>();

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        // plus the length of both state sets
        + CheckpointStatesEncoder.sbeHeaderSize()
        + checkpointStates.size() * CheckpointStatesEncoder.sbeBlockLength()
        + BackupStatesEncoder.sbeHeaderSize()
        + backupStates.size() * BackupStatesEncoder.sbeBlockLength()
        + LogArchiveStatesEncoder.sbeHeaderSize()
        + logArchiveStates.size() * LogArchiveStatesEncoder.sbeBlockLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);

    final var checkpointStateEncoder = bodyEncoder.checkpointStatesCount(checkpointStates.size());
    for (final PartitionCheckpointState partitionState : checkpointStates) {
      checkpointStateEncoder
          .next()
          .partitionId(partitionState.partitionId)
          .checkpointId(partitionState.checkpointId)
          .checkpointType(partitionState.checkpointType.getValue())
          .checkpointTimestamp(partitionState.checkpointTimestamp)
          .checkpointPosition(partitionState.checkpointPosition);
    }

    final var backupStateEncoder = bodyEncoder.backupStatesCount(backupStates.size());
    for (final PartitionCheckpointState partitionState : backupStates) {
      backupStateEncoder
          .next()
          .partitionId(partitionState.partitionId)
          .checkpointId(partitionState.checkpointId)
          .checkpointType(partitionState.checkpointType.getValue())
          .checkpointTimestamp(partitionState.checkpointTimestamp)
          .checkpointPosition(partitionState.checkpointPosition)
          .firstLogPosition(partitionState.firstLogPosition);
    }

    // the group is always written, with a count of zero if empty
    final var logArchiveStateEncoder = bodyEncoder.logArchiveStatesCount(logArchiveStates.size());
    for (final PartitionLogArchiveState partitionState : logArchiveStates) {
      logArchiveStateEncoder
          .next()
          .partitionId(partitionState.partitionId)
          .timelineStarted((short) (partitionState.timelineStarted ? 1 : 0))
          .archivedPosition(partitionState.archivedPosition)
          .newestArchivedSnapshotProcessedPosition(
              partitionState.newestArchivedSnapshotProcessedPosition)
          .handoverCheckpointId(partitionState.handoverCheckpointId);
    }
    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

    bodyDecoder
        .checkpointStates()
        .forEach(
            state ->
                checkpointStates.add(
                    new PartitionCheckpointState(
                        state.partitionId(),
                        state.checkpointId(),
                        CheckpointType.valueOf(state.checkpointType()),
                        state.checkpointTimestamp(),
                        state.checkpointPosition())));

    bodyDecoder
        .backupStates()
        .forEach(
            state ->
                backupStates.add(
                    new PartitionCheckpointState(
                        state.partitionId(),
                        state.checkpointId(),
                        CheckpointType.valueOf(state.checkpointType()),
                        state.checkpointTimestamp(),
                        state.checkpointPosition(),
                        state.firstLogPosition())));

    // empty when the message was encoded with schema version 2 or lower
    bodyDecoder
        .logArchiveStates()
        .forEach(
            state ->
                logArchiveStates.add(
                    new PartitionLogArchiveState(
                        state.partitionId(),
                        state.timelineStarted() != 0,
                        state.archivedPosition(),
                        state.newestArchivedSnapshotProcessedPosition(),
                        state.handoverCheckpointId())));
  }

  public Set<PartitionCheckpointState> getCheckpointStates() {
    return checkpointStates;
  }

  public void setCheckpointStates(final Set<PartitionCheckpointState> checkpointStates) {
    this.checkpointStates = checkpointStates;
  }

  public Set<PartitionCheckpointState> getBackupStates() {
    return backupStates;
  }

  public void setBackupStates(final Set<PartitionCheckpointState> backupStates) {
    this.backupStates = backupStates;
  }

  public Set<PartitionLogArchiveState> getLogArchiveStates() {
    return logArchiveStates;
  }

  public void setLogArchiveStates(final Set<PartitionLogArchiveState> logArchiveStates) {
    this.logArchiveStates = logArchiveStates;
  }

  /**
   * The log archive state of a partition.
   *
   * @param newestArchivedSnapshotProcessedPosition -1 if no snapshot is archived
   * @param handoverCheckpointId -1 if no handover is recorded
   */
  public record PartitionLogArchiveState(
      int partitionId,
      boolean timelineStarted,
      long archivedPosition,
      long newestArchivedSnapshotProcessedPosition,
      long handoverCheckpointId) {}

  public record PartitionCheckpointState(
      int partitionId,
      long checkpointId,
      CheckpointType checkpointType,
      long checkpointTimestamp,
      long checkpointPosition,
      long firstLogPosition) {

    /** Constructor without firstLogPosition for checkpoint states (e.g. MARKER). */
    public PartitionCheckpointState(
        final int partitionId,
        final long checkpointId,
        final CheckpointType checkpointType,
        final long checkpointTimestamp,
        final long checkpointPosition) {
      this(partitionId, checkpointId, checkpointType, checkpointTimestamp, checkpointPosition, -1L);
    }
  }
}
