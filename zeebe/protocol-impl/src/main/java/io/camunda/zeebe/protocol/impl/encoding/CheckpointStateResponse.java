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
import io.camunda.zeebe.protocol.management.CheckpointStateResponseEncoder.StateEncoder;
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

  // This holds the earliest checkpoint state amongst partitions. For the inter-broker response,
  // this will hold the state of the partition.
  private PartitionCheckpointState checkpointState = new PartitionCheckpointState();
  private Set<PartitionCheckpointState> checkpointStates = new HashSet<>();

  @Override
  public int getLength() {
    final int latestLength =
        CheckpointStateResponseEncoder.partitionIdEncodingLength()
            + CheckpointStateResponseEncoder.checkpointIdEncodingLength()
            + CheckpointStateResponseEncoder.checkpointTimestampEncodingLength()
            + CheckpointStateResponseEncoder.checkpointTypeEncodingLength()
            + CheckpointStateResponseEncoder.checkpointPositionEncodingLength();

    final int stateLength =
        checkpointStates.stream()
            .map(
                state ->
                    StateEncoder.sbeBlockLength()
                        + StateEncoder.partitionIdEncodingLength()
                        + StateEncoder.checkpointIdEncodingLength()
                        + StateEncoder.checkpointTypeEncodingLength()
                        + StateEncoder.checkpointTimestampEncodingLength()
                        + StateEncoder.checkpointPositionEncodingLength())
            .reduce(Integer::sum)
            .orElse(0);

    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + latestLength
        + StateEncoder.HEADER_SIZE
        + stateLength;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .partitionId(checkpointState.partitionId)
        .checkpointId(checkpointState.checkpointId)
        .checkpointType(checkpointState.checkpointType.getValue())
        .checkpointTimestamp(checkpointState.checkpointTimestamp)
        .checkpointPosition(checkpointState.checkpointPosition);

    final var stateEncoder = bodyEncoder.stateCount(checkpointStates.size());
    for (final PartitionCheckpointState checkpointState : checkpointStates) {
      stateEncoder
          .next()
          .partitionId(checkpointState.partitionId)
          .checkpointId(checkpointState.checkpointId)
          .checkpointType(checkpointState.checkpointType.getValue())
          .checkpointTimestamp(checkpointState.checkpointTimestamp)
          .checkpointPosition(checkpointState.checkpointPosition);
    }
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    final var partitionId = bodyDecoder.partitionId();
    final var checkpointId = bodyDecoder.checkpointId();
    final var checkpointType = CheckpointType.valueOf(bodyDecoder.checkpointType());
    final var checkpointTimestamp = bodyDecoder.checkpointTimestamp();
    final var checkpointPosition = bodyDecoder.checkpointPosition();
    checkpointState =
        new PartitionCheckpointState(
            partitionId, checkpointId, checkpointType, checkpointTimestamp, checkpointPosition);

    bodyDecoder
        .state()
        .forEach(
            state ->
                checkpointStates.add(
                    new PartitionCheckpointState(
                        state.partitionId(),
                        state.checkpointId(),
                        CheckpointType.valueOf(state.checkpointType()),
                        state.checkpointTimestamp(),
                        state.checkpointPosition())));
  }

  public PartitionCheckpointState getCheckpointState() {
    return checkpointState;
  }

  public void setPartitionId(final int partitionId) {
    checkpointState = checkpointState.withPartitionId(partitionId);
  }

  public void setCheckpointId(final long checkpointId) {
    checkpointState = checkpointState.withCheckpointId(checkpointId);
  }

  public void setCheckpointType(final CheckpointType checkpointType) {
    checkpointState = checkpointState.withCheckpointType(checkpointType);
  }

  public void setCheckpointTimestamp(final long checkpointTimestamp) {
    checkpointState = checkpointState.withCheckpointTimestamp(checkpointTimestamp);
  }

  public void setCheckpointPosition(final long checkpointPosition) {
    checkpointState = checkpointState.withCheckpointPosition(checkpointPosition);
  }

  public Set<PartitionCheckpointState> getCheckpointStates() {
    return checkpointStates;
  }

  public void setCheckpointStates(final Set<PartitionCheckpointState> checkpointStates) {
    this.checkpointStates = checkpointStates;
  }

  public record PartitionCheckpointState(
      int partitionId,
      long checkpointId,
      CheckpointType checkpointType,
      long checkpointTimestamp,
      long checkpointPosition) {

    public PartitionCheckpointState() {
      this(0, 0L, CheckpointType.MARKER, 0L, 0L);
    }

    public PartitionCheckpointState withPartitionId(final int partitionId) {
      return new PartitionCheckpointState(
          partitionId, checkpointId, checkpointType, checkpointTimestamp, checkpointPosition);
    }

    public PartitionCheckpointState withCheckpointId(final long checkpointId) {
      return new PartitionCheckpointState(
          partitionId, checkpointId, checkpointType, checkpointTimestamp, checkpointPosition);
    }

    public PartitionCheckpointState withCheckpointType(final CheckpointType checkpointType) {
      return new PartitionCheckpointState(
          partitionId, checkpointId, checkpointType, checkpointTimestamp, checkpointPosition);
    }

    public PartitionCheckpointState withCheckpointTimestamp(final long checkpointTimestamp) {
      return new PartitionCheckpointState(
          partitionId, checkpointId, checkpointType, checkpointTimestamp, checkpointPosition);
    }

    public PartitionCheckpointState withCheckpointPosition(final long checkpointPosition) {
      return new PartitionCheckpointState(
          partitionId, checkpointId, checkpointType, checkpointTimestamp, checkpointPosition);
    }
  }
}
