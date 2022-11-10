/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.agrona.collections.ArrayUtil.EMPTY_BYTE_ARRAY;

import io.camunda.zeebe.protocol.management.BackupListResponseDecoder;
import io.camunda.zeebe.protocol.management.BackupListResponseDecoder.BackupsDecoder;
import io.camunda.zeebe.protocol.management.BackupListResponseEncoder;
import io.camunda.zeebe.protocol.management.BackupListResponseEncoder.BackupsEncoder;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class BackupListResponse implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final BackupListResponseEncoder bodyEncoder = new BackupListResponseEncoder();
  private final BackupListResponseDecoder bodyDecoder = new BackupListResponseDecoder();

  private List<BackupStatus> backups;

  public BackupListResponse(final List<BackupStatus> statuses) {
    backups = statuses;
  }

  public BackupListResponse(final DirectBuffer buffer, final int offset, final int length) {
    wrap(buffer, offset, length);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    backups = new ArrayList<>();
    for (final var backupsDecoder : bodyDecoder.backups()) {
      final var backup =
          new BackupStatus()
              .setBackupId(backupsDecoder.backupId())
              .setPartitionId(backupsDecoder.partitionId())
              .setStatus(backupsDecoder.status());

      backup.encodedFailureReason = new byte[backupsDecoder.failureReasonLength()];
      backupsDecoder.getFailureReason(
          backup.encodedFailureReason, 0, backup.encodedFailureReason.length);
      backup.failureReason =
          decodeString(
              backup.encodedFailureReason, BackupsDecoder.failureReasonCharacterEncoding());

      backup.encodedCreatedAt = new byte[backupsDecoder.createdAtLength()];
      backupsDecoder.getCreatedAt(backup.encodedCreatedAt, 0, backup.encodedCreatedAt.length);
      backup.createdAt =
          decodeString(backup.encodedCreatedAt, BackupsDecoder.createdAtCharacterEncoding());

      backup.encodedBrokerVersion = new byte[backupsDecoder.brokerVersionLength()];
      backupsDecoder.getBrokerVersion(
          backup.encodedBrokerVersion, 0, backup.encodedBrokerVersion.length);
      backup.brokerVersion =
          decodeString(
              backup.encodedBrokerVersion, BackupsDecoder.brokerVersionCharacterEncoding());
      backups.add(backup);
    }
  }

  private static String decodeString(final byte[] encodedSnapshotId, final String charsetName) {
    try {
      return new String(encodedSnapshotId, charsetName);
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getLength() {
    final int backupsLength =
        backups.stream()
            .map(
                backup ->
                    BackupsEncoder.sbeBlockLength()
                        + BackupsEncoder.failureReasonHeaderLength()
                        + backup.encodedFailureReason.length
                        + BackupsEncoder.brokerVersionHeaderLength()
                        + backup.encodedBrokerVersion.length
                        + BackupsEncoder.createdAtHeaderLength()
                        + backup.encodedCreatedAt.length)
            .reduce(Integer::sum)
            .orElse(0);

    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + BackupsEncoder.HEADER_SIZE
        + backupsLength;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    final var backupsEncoder = bodyEncoder.backupsCount(backups.size());
    backups.forEach(
        backup ->
            backupsEncoder
                .next()
                .backupId(backup.backupId)
                .partitionId(backup.partitionId)
                .status(backup.status)
                .putFailureReason(
                    backup.encodedFailureReason, 0, backup.encodedFailureReason.length)
                .putCreatedAt(backup.encodedCreatedAt, 0, backup.encodedCreatedAt.length)
                .putBrokerVersion(
                    backup.encodedBrokerVersion, 0, backup.encodedBrokerVersion.length));
  }

  public List<BackupStatus> getBackups() {
    return backups;
  }

  public static class BackupStatus {
    private long backupId;

    private int partitionId;
    private BackupStatusCode status;

    private String failureReason = "";
    private byte[] encodedFailureReason = EMPTY_BYTE_ARRAY;
    private String brokerVersion = "";
    private byte[] encodedBrokerVersion = EMPTY_BYTE_ARRAY;
    private String createdAt = "";
    private byte[] encodedCreatedAt = EMPTY_BYTE_ARRAY;

    public long getBackupId() {
      return backupId;
    }

    public BackupStatus setBackupId(final long backupId) {
      this.backupId = backupId;
      return this;
    }

    public int getPartitionId() {
      return partitionId;
    }

    public BackupStatus setPartitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public BackupStatusCode getStatus() {
      return status;
    }

    public BackupStatus setStatus(final BackupStatusCode status) {
      this.status = status;
      return this;
    }

    public String getFailureReason() {
      return failureReason;
    }

    public BackupStatus setFailureReason(final String failureReason) {
      this.failureReason = failureReason;
      encodedFailureReason =
          encodeString(failureReason, BackupsEncoder.failureReasonCharacterEncoding());
      return this;
    }

    public String getBrokerVersion() {
      return brokerVersion;
    }

    public BackupStatus setBrokerVersion(final String brokerVersion) {
      this.brokerVersion = brokerVersion;
      encodedBrokerVersion =
          encodeString(brokerVersion, BackupsEncoder.brokerVersionCharacterEncoding());
      return this;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    public BackupStatus setCreatedAt(final String createdAt) {
      this.createdAt = createdAt;
      encodedCreatedAt = encodeString(createdAt, BackupsEncoder.createdAtCharacterEncoding());
      return this;
    }

    @Override
    public int hashCode() {
      int result = (int) (backupId ^ (backupId >>> 32));
      result = 31 * result + partitionId;
      result = 31 * result + status.hashCode();
      result = 31 * result + failureReason.hashCode();
      result = 31 * result + brokerVersion.hashCode();
      result = 31 * result + createdAt.hashCode();
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final BackupStatus that = (BackupStatus) o;

      if (backupId != that.backupId) {
        return false;
      }
      if (partitionId != that.partitionId) {
        return false;
      }
      if (status != that.status) {
        return false;
      }
      if (!failureReason.equals(that.failureReason)) {
        return false;
      }
      if (!brokerVersion.equals(that.brokerVersion)) {
        return false;
      }
      return createdAt.equals(that.createdAt);
    }

    @Override
    public String toString() {
      return "BackupStatus{"
          + "backupId="
          + backupId
          + ", partitionId="
          + partitionId
          + ", status="
          + status
          + ", failureReason='"
          + failureReason
          + '\''
          + ", brokerVersion='"
          + brokerVersion
          + '\''
          + ", createdAt='"
          + createdAt
          + '\''
          + '}';
    }

    private byte[] encodeString(final String value, final String charsetName) {
      try {
        return (null == value || value.isEmpty()) ? EMPTY_BYTE_ARRAY : value.getBytes(charsetName);
      } catch (final UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
