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
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class CheckpointStateResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final CheckpointStateResponseEncoder bodyEncoder = new CheckpointStateResponseEncoder();
  private final CheckpointStateResponseDecoder bodyDecoder = new CheckpointStateResponseDecoder();

  private int partitionId;
  private long checkpointId;
  private CheckpointType checkpointType;
  private long checkpointTimestamp;
  private long checkpointPosition;

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + CheckpointStateResponseEncoder.partitionIdEncodingLength()
        + CheckpointStateResponseEncoder.checkpointTypeEncodingLength()
        + CheckpointStateResponseEncoder.checkpointIdEncodingLength()
        + CheckpointStateResponseEncoder.checkpointTimestampEncodingLength()
        + CheckpointStateResponseEncoder.checkpointPositionEncodingLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .partitionId(partitionId)
        .checkpointId(checkpointId)
        .checkpointType(checkpointType.getValue())
        .checkpointTimestamp(checkpointTimestamp)
        .checkpointPosition(checkpointPosition);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    partitionId = bodyDecoder.partitionId();
    checkpointId = bodyDecoder.checkpointId();
    checkpointType = CheckpointType.valueOf(bodyDecoder.checkpointType());
    checkpointTimestamp = bodyDecoder.checkpointTimestamp();
    checkpointPosition = bodyDecoder.checkpointPosition();
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public long getCheckpointId() {
    return checkpointId;
  }

  public void setCheckpointId(final long checkpointId) {
    this.checkpointId = checkpointId;
  }

  public CheckpointType getCheckpointType() {
    return checkpointType;
  }

  public void setCheckpointType(final CheckpointType checkpointType) {
    this.checkpointType = checkpointType;
  }

  public long getCheckpointTimestamp() {
    return checkpointTimestamp;
  }

  public void setCheckpointTimestamp(final long checkpointTimestamp) {
    this.checkpointTimestamp = checkpointTimestamp;
  }

  public long getCheckpointPosition() {
    return checkpointPosition;
  }

  public void setCheckpointPosition(final long checkpointPosition) {
    this.checkpointPosition = checkpointPosition;
  }
}
