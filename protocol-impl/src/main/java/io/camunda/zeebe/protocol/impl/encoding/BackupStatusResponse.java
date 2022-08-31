/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.agrona.collections.ArrayUtil.EMPTY_BYTE_ARRAY;

import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.management.BackupStatusResponseDecoder;
import io.camunda.zeebe.protocol.management.BackupStatusResponseEncoder;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.io.UnsupportedEncodingException;
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
  private String snapshotId = "";
  private byte[] encodedSnapshotId = EMPTY_BYTE_ARRAY;
  private String failureReason = "";
  private byte[] encodedFailureReason = EMPTY_BYTE_ARRAY;

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
    encodedSnapshotId =
        encodeString(snapshotId, BackupStatusResponseEncoder.snapshotIdCharacterEncoding());
    return this;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public BackupStatusResponse setFailureReason(final String failureReason) {
    this.failureReason = failureReason;
    encodedFailureReason =
        encodeString(failureReason, BackupStatusResponseEncoder.failureReasonCharacterEncoding());
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

    encodedSnapshotId = new byte[bodyDecoder.snapshotIdLength()];
    bodyDecoder.getSnapshotId(encodedSnapshotId, 0, encodedSnapshotId.length);
    snapshotId =
        decodeString(encodedSnapshotId, BackupStatusResponseDecoder.snapshotIdCharacterEncoding());

    encodedFailureReason = new byte[bodyDecoder.failureReasonLength()];
    bodyDecoder.getFailureReason(encodedFailureReason, 0, encodedFailureReason.length);
    failureReason =
        decodeString(
            encodedFailureReason, BackupStatusResponseDecoder.failureReasonCharacterEncoding());
  }

  private String decodeString(final byte[] encodedSnapshotId, final String charsetName) {
    try {
      return new String(encodedSnapshotId, charsetName);
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + BackupStatusResponseEncoder.snapshotIdHeaderLength()
        + encodedSnapshotId.length
        + BackupStatusResponseEncoder.failureReasonHeaderLength()
        + encodedFailureReason.length;
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
        .putSnapshotId(encodedSnapshotId, 0, encodedSnapshotId.length)
        .putFailureReason(encodedFailureReason, 0, encodedFailureReason.length);
  }

  private byte[] encodeString(final String value, final String charsetName) {
    try {
      return (null == value || value.isEmpty()) ? EMPTY_BYTE_ARRAY : value.getBytes(charsetName);
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
