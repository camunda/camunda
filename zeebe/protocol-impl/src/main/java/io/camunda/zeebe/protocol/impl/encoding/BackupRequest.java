/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.management.BackupRequestDecoder;
import io.camunda.zeebe.protocol.management.BackupRequestEncoder;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BackupRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final BackupRequestEncoder bodyEncoder = new BackupRequestEncoder();
  private final BackupRequestDecoder bodyDecoder = new BackupRequestDecoder();
  private BackupRequestType type;
  private int partitionId;
  private long backupId;
  private String pattern;
  private CheckpointType checkpointType;

  public BackupRequest reset() {
    type = BackupRequestType.NULL_VAL;
    partitionId = BackupRequestEncoder.partitionIdNullValue();
    backupId = BackupRequestEncoder.backupIdNullValue();
    pattern = null;
    return this;
  }

  public BackupRequestType getType() {
    return type;
  }

  public BackupRequest setType(final BackupRequestType type) {
    this.type = type;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public BackupRequest setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getBackupId() {
    return backupId;
  }

  public BackupRequest setBackupId(final long backupId) {
    this.backupId = backupId;
    return this;
  }

  public String getPattern() {
    return pattern;
  }

  public BackupRequest setPattern(final String pattern) {
    this.pattern = pattern;
    return this;
  }

  public CheckpointType getCheckpointType() {
    return checkpointType;
  }

  public BackupRequest setCheckpointType(final CheckpointType checkpointType) {
    this.checkpointType = checkpointType;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    partitionId = bodyDecoder.partitionId();
    type = bodyDecoder.type();
    backupId = bodyDecoder.backupId();
    pattern = bodyDecoder.pattern();
    if (bodyDecoder.checkpointType() != BackupRequestEncoder.checkpointTypeNullValue()) {
      checkpointType = CheckpointType.valueOf(bodyDecoder.checkpointType());
    }
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + BackupRequestEncoder.patternHeaderLength()
        + (pattern == null ? 0 : pattern.length());
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .partitionId(partitionId)
        .type(type)
        .backupId(backupId)
        .pattern(pattern);

    if (checkpointType != null) {
      bodyEncoder.checkpointType(checkpointType.getValue());
    }
    return getLength();
  }
}
