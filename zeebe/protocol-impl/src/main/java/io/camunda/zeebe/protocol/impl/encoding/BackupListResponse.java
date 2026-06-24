/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

  // Consists of backup statuses with encoded string
  private List<InternalBackupStatus> internalBackups;

  public BackupListResponse() {
    // Need an empty constructor to be used in the gateway
  }

  public BackupListResponse(final List<BackupStatus> statuses) {
    internalBackups = statuses.stream().map(InternalBackupStatus::new).toList();
  }

  public BackupListResponse(final DirectBuffer buffer, final int offset, final int length) {
    wrap(buffer, offset, length);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    internalBackups = new ArrayList<>();
    for (final var backupsDecoder : bodyDecoder.backups()) {

      final byte[] encodedFailureReason = new byte[backupsDecoder.failureReasonLength()];
      backupsDecoder.getFailureReason(encodedFailureReason, 0, encodedFailureReason.length);

      final byte[] encodedCreatedAt = new byte[backupsDecoder.createdAtLength()];
      backupsDecoder.getCreatedAt(encodedCreatedAt, 0, encodedCreatedAt.length);

      final byte[] encodedBrokerVersion = new byte[backupsDecoder.brokerVersionLength()];
      backupsDecoder.getBrokerVersion(encodedBrokerVersion, 0, encodedBrokerVersion.length);

      internalBackups.add(
          new InternalBackupStatus(
              backupsDecoder.backupId(),
              backupsDecoder.partitionId(),
              backupsDecoder.status(),
              encodedFailureReason,
              encodedBrokerVersion,
              encodedCreatedAt));
    }
  }

  @Override
  public int getLength() {
    final int backupsLength =
        internalBackups.stream()
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
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    final var backupsEncoder = bodyEncoder.backupsCount(internalBackups.size());
    internalBackups.forEach(
        backup ->
            backupsEncoder
                .next()
                .backupId(backup.status.backupId)
                .partitionId(backup.status.partitionId)
                .status(backup.status.status)
                .putFailureReason(
                    backup.encodedFailureReason, 0, backup.encodedFailureReason.length)
                .putCreatedAt(backup.encodedCreatedAt, 0, backup.encodedCreatedAt.length)
                .putBrokerVersion(
                    backup.encodedBrokerVersion, 0, backup.encodedBrokerVersion.length));
    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }

  public List<BackupStatus> getBackups() {
    return internalBackups.stream().map(s -> s.status).toList();
  }

  public record BackupStatus(
      long backupId,
      int partitionId,
      BackupStatusCode status,
      String failureReason,
      String brokerVersion,
      String createdAt) {}

  private static final class InternalBackupStatus {
    private final BackupStatus status;
    private final byte[] encodedFailureReason;
    private final byte[] encodedBrokerVersion;
    private final byte[] encodedCreatedAt;

    InternalBackupStatus(final BackupStatus status) {
      this.status = status;
      encodedFailureReason =
          encodeString(status.failureReason(), BackupsEncoder.failureReasonCharacterEncoding());
      encodedBrokerVersion =
          encodeString(status.brokerVersion(), BackupsEncoder.brokerVersionCharacterEncoding());
      encodedCreatedAt =
          encodeString(status.createdAt(), BackupsEncoder.createdAtCharacterEncoding());
    }

    InternalBackupStatus(
        final long backupId,
        final int partitionId,
        final BackupStatusCode statusCode,
        final byte[] encodedFailureReason,
        final byte[] encodedBrokerVersion,
        final byte[] encodedCreatedAt) {
      status =
          new BackupStatus(
              backupId,
              partitionId,
              statusCode,
              decodeString(encodedFailureReason, BackupsDecoder.failureReasonCharacterEncoding()),
              decodeString(encodedBrokerVersion, BackupsDecoder.brokerVersionCharacterEncoding()),
              decodeString(encodedCreatedAt, BackupsDecoder.createdAtCharacterEncoding()));
      this.encodedCreatedAt = encodedCreatedAt;
      this.encodedBrokerVersion = encodedBrokerVersion;
      this.encodedFailureReason = encodedFailureReason;
    }

    private static byte[] encodeString(final String value, final String charsetName) {
      try {
        return (null == value || value.isEmpty()) ? EMPTY_BYTE_ARRAY : value.getBytes(charsetName);
      } catch (final UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }

    private static String decodeString(final byte[] value, final String charsetName) {
      try {
        return new String(value, charsetName);
      } catch (final UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
