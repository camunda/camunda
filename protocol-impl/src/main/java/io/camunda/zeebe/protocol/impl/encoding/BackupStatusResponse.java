/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.management.BackupStatusResponseDecoder;
import io.camunda.zeebe.protocol.management.BackupStatusResponseEncoder;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BackupStatusResponse implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final BackupStatusResponseEncoder bodyEncoder = new BackupStatusResponseEncoder();
  private final BackupStatusResponseDecoder bodyDecoder = new BackupStatusResponseDecoder();

  private long backupId;

  private int partitionId;
  private int brokerId;
  private BackupStatusCode status;

  private long checkpointPosition = BackupStatusResponseEncoder.checkpointPositionNullValue();
  private int numberOfPartitions = BackupStatusResponseEncoder.numberOfPartitionsNullValue();
  private String snapshotId = null;
  private String failureReason = null;

  public long getBackupId() {
    return backupId;
  }

  public BackupStatusResponse setBackupId(final long backupId) {
    this.backupId = backupId;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public BackupStatusResponse setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public int getBrokerId() {
    return brokerId;
  }

  public BackupStatusResponse setBrokerId(final int brokerId) {
    this.brokerId = brokerId;
    return this;
  }

  public BackupStatusCode getStatus() {
    return status;
  }

  public BackupStatusResponse setStatus(final BackupStatusCode status) {
    this.status = status;
    return this;
  }

  public long getCheckpointPosition() {
    return checkpointPosition;
  }

  public BackupStatusResponse setCheckpointPosition(final long checkpointPosition) {
    this.checkpointPosition = checkpointPosition;
    return this;
  }

  public int getNumberOfPartitions() {
    return numberOfPartitions;
  }

  public BackupStatusResponse setNumberOfPartitions(final int numberOfPartitions) {
    this.numberOfPartitions = numberOfPartitions;
    return this;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  public BackupStatusResponse setSnapshotId(final String snapshotId) {
    this.snapshotId = snapshotId;
    return this;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public BackupStatusResponse setFailureReason(final String failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

    backupId = bodyDecoder.backupId();
    partitionId = bodyDecoder.partitionId();
    status = bodyDecoder.status();
    brokerId = bodyDecoder.brokerId();
    checkpointPosition = bodyDecoder.checkpointPosition();
    numberOfPartitions = bodyDecoder.numberOfPartitions();
    snapshotId = bodyDecoder.snapshotId();
    failureReason = bodyDecoder.failureReason();
  }

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {

    bodyEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    bodyEncoder
        .backupId(backupId)
        .brokerId(brokerId)
        .partitionId(partitionId)
        .checkpointPosition(checkpointPosition)
        .numberOfPartitions(numberOfPartitions)
        .status(status)
        .snapshotId(snapshotId)
        .failureReason(failureReason);
  }
}
