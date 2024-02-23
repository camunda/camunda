/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.management.BackupRequestDecoder;
import io.camunda.zeebe.protocol.management.BackupRequestEncoder;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
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

  public BackupRequest reset() {
    type = BackupRequestType.NULL_VAL;
    partitionId = BackupRequestEncoder.partitionIdNullValue();
    backupId = BackupRequestEncoder.backupIdNullValue();
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

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    partitionId = bodyDecoder.partitionId();
    type = bodyDecoder.type();
    backupId = bodyDecoder.backupId();
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .partitionId(partitionId)
        .type(type)
        .backupId(backupId);
  }
}
