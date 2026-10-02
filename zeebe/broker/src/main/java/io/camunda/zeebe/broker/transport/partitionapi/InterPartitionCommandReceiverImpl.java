/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.protocol.InterPartitionMessageDecoder;
import io.camunda.zeebe.broker.protocol.MessageHeaderDecoder;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

final class InterPartitionCommandReceiverImpl {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final Logger throttledLog = new ThrottledLogger(LOG, Duration.ofSeconds(15));
  private final Decoder decoder = new Decoder();
  private final LogStreamWriter logStreamWriter;
  private final PersistedSnapshotStore snapshotStore;
  private final ConcurrencyControl concurrencyControl;
  private boolean diskSpaceAvailable = true;
  private long checkpointId = CheckpointState.NO_CHECKPOINT;
  private CheckpointType checkpointType = null;
  private long inFlightCheckpointId = CheckpointState.NO_CHECKPOINT;

  InterPartitionCommandReceiverImpl(
      final LogStreamWriter logStreamWriter,
      final PersistedSnapshotStore snapshotStore,
      final ConcurrencyControl concurrencyControl) {
    this.logStreamWriter = logStreamWriter;
    this.snapshotStore = snapshotStore;
    this.concurrencyControl = concurrencyControl;
  }

  ActorFuture<Void> handleMessage(final MemberId memberId, final byte[] message) {
    LOG.trace("Received message from {}", memberId);

    final var decoded = decoder.decodeMessage(message);

    if (!diskSpaceAvailable) {
      LOG.warn(
          "Ignoring command {} {} from {}, checkpoint {}, no disk space available",
          decoded.metadata.getValueType(),
          decoded.metadata.getIntent(),
          memberId,
          decoded.checkpointId);
      return CompletableActorFuture.completed();
    }

    if (shouldTakeSnapshot(decoded)) {
      return snapshotStore
          .reserveLatestSnapshot(decoded.checkpointId)
          .andThen(
              (snapshotId, error) -> {
                if (error != null) {
                  LOG.warn(
                      "Failed to reserve a snapshot for checkpoint {}",
                      decoded.checkpointId,
                      error);
                }
                writeCheckpointAndCommand(
                    memberId, decoded, error == null ? snapshotId.orElse("") : "");
                return CompletableActorFuture.completed();
              },
              concurrencyControl);
    }

    writeCheckpointAndCommand(memberId, decoded, "");
    return CompletableActorFuture.completed();
  }

  private void writeCheckpointAndCommand(
      final MemberId memberId, final DecodedMessage decoded, final String snapshotId) {
    if (!isNewCheckpoint(decoded)) {
      // created meanwhile, e.g. by the backup request sent to all partitions: the reservation
      // isn't carried by any checkpoint
      releaseSnapshotReservation(decoded.checkpointId, snapshotId);
    }
    final var checkpointWritten = writeCheckpoint(decoded, snapshotId);

    if (checkpointWritten.isLeft()) {
      // It's unsafe to write this record without first writing the checkpoint, bail out early.
      logCheckpointFailure(memberId, decoded, checkpointWritten);
      releaseSnapshotReservation(decoded.checkpointId, snapshotId);
      return;
    }

    writeCommand(decoded).ifLeft(failure -> logWriteFailure(memberId, decoded, failure));
  }

  private void logCheckpointFailure(
      final MemberId memberId,
      final DecodedMessage decoded,
      final Either<WriteFailure, Long> checkpointWritten) {
    LOG.warn(
        "Failed to write new command for checkpoint {} (currently at {}), ignoring command {} {} from {} (error = {})",
        decoded.checkpointId,
        checkpointId,
        decoded.metadata.getValueType(),
        decoded.metadata.getIntent(),
        memberId,
        checkpointWritten.getLeft());
  }

  private void logWriteFailure(
      final MemberId memberId, final DecodedMessage decoded, final WriteFailure failure) {
    throttledLog.warn(
        "Failed to write received command {}.{} to logstream (error = {}, sender = {})",
        decoded.metadata.getValueType(),
        decoded.metadata.getIntent(),
        failure,
        memberId);
  }

  private boolean shouldTakeSnapshot(final DecodedMessage decoded) {
    return isNewCheckpoint(decoded) && decoded.checkpointType.shouldCreateBackup();
  }

  private boolean isNewCheckpoint(final DecodedMessage decoded) {
    return decoded.checkpointId > Math.max(checkpointId, inFlightCheckpointId);
  }

  private void releaseSnapshotReservation(final long checkpointId, final String snapshotId) {
    if (!snapshotId.isEmpty()) {
      snapshotStore.releaseReservation(checkpointId, snapshotId);
    }
  }

  private Either<WriteFailure, Long> writeCheckpoint(
      final DecodedMessage decoded, final String snapshotId) {
    if (!isNewCheckpoint(decoded)) {
      // No need to write a new checkpoint create record
      return Either.right(checkpointId);
    }

    LOG.debug(
        "Received command with checkpoint id {} and type {} , current checkpoint id {} and type {}",
        decoded.checkpointId,
        decoded.checkpointType,
        checkpointId,
        checkpointType);
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(CheckpointIntent.CREATE)
            .valueType(ValueType.CHECKPOINT);
    final var checkpointRecord =
        new CheckpointRecord()
            .setCheckpointId(decoded.checkpointId)
            .setCheckpointType(decoded.checkpointType)
            .setSnapshotId(snapshotId);
    final var written =
        logStreamWriter.tryWrite(
            WriteContext.interPartition(), LogAppendEntry.of(metadata, checkpointRecord));
    if (written.isRight()) {
      inFlightCheckpointId = decoded.checkpointId;
    }
    return written;
  }

  private Either<WriteFailure, Long> writeCommand(final DecodedMessage decoded) {
    final var appendEntry =
        decoded
            .recordKey()
            .map(key -> LogAppendEntry.of(key, decoded.metadata(), decoded.command()))
            .orElseGet(() -> LogAppendEntry.of(decoded.metadata(), decoded.command()));

    return logStreamWriter.tryWrite(WriteContext.interPartition(), appendEntry);
  }

  void setDiskSpaceAvailable(final boolean available) {
    diskSpaceAvailable = available;
  }

  void setCheckpointInfo(final long checkpointId, final CheckpointType checkpointType) {
    this.checkpointId = checkpointId;
    this.checkpointType = checkpointType;
  }

  private record DecodedMessage(
      long checkpointId,
      CheckpointType checkpointType,
      Optional<Long> recordKey,
      RecordMetadata metadata,
      UnifiedRecordValue command) {}

  private static final class Decoder {
    private final InterPartitionMessageDecoder messageDecoder = new InterPartitionMessageDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    DecodedMessage decodeMessage(final byte[] message) {
      messageDecoder.wrapAndApplyHeader(new UnsafeBuffer(message), 0, headerDecoder);

      final var checkpointId = messageDecoder.checkpointId();
      Optional<Long> recordKey = Optional.empty();
      if (messageDecoder.recordKey() != InterPartitionMessageDecoder.recordKeyNullValue()) {
        recordKey = Optional.of(messageDecoder.recordKey());
      }

      final var recordMetadata = new RecordMetadata();
      final var value = decodeCommand(messageDecoder, recordMetadata);
      decodeAuthInfo(messageDecoder, recordMetadata);

      // Default to MANUAL_BACKUP if no checkpoint type is set for backward compatibility
      final CheckpointType checkpointType;
      if (messageDecoder.checkpointType()
          != InterPartitionMessageDecoder.checkpointTypeNullValue()) {
        checkpointType = CheckpointType.valueOf(messageDecoder.checkpointType());
      } else {
        checkpointType = CheckpointType.MANUAL_BACKUP;
      }

      return new DecodedMessage(checkpointId, checkpointType, recordKey, recordMetadata, value);
    }

    /**
     * Reads out the command from the message decoder and updates the record metadata accordingly.
     *
     * @param messageDecoder The current message decoder, with {@link
     *     InterPartitionMessageDecoder#limit()} pointing to the start of the command. After reading
     *     the command, the decoder is advanced to the next section.
     * @param recordMetadata The record metadata to update with record type, intent and value type.
     * @return a new instance of the command, read from the message decoder.
     */
    private static UnifiedRecordValue decodeCommand(
        final InterPartitionMessageDecoder messageDecoder, final RecordMetadata recordMetadata) {
      final var offset =
          messageDecoder.limit() + InterPartitionMessageDecoder.commandHeaderLength();
      final var commandLength = messageDecoder.commandLength();

      final var valueType = ValueType.get(messageDecoder.valueType());
      final var intent = Intent.fromProtocolValue(valueType, messageDecoder.intent());
      recordMetadata.recordType(RecordType.COMMAND).intent(intent).valueType(valueType);

      final var value = UnifiedRecordValue.fromValueType(valueType);
      if (value == null) {
        throw new IllegalArgumentException(
            "No value type mapped to %s, can't decode message".formatted(valueType));
      }
      value.wrap(messageDecoder.buffer(), offset, commandLength);
      messageDecoder.skipCommand();
      return value;
    }

    /**
     * Reads out the authorization info from the message decoder and updates the record metadata
     * accordingly.
     *
     * @param messageDecoder The current message decoder, with {@link
     *     InterPartitionMessageDecoder#limit()} pointing to the start of the auth info. After
     *     reading the auth info, the decoder is advanced to the next section.
     * @param recordMetadata The record metadata to update with the authorization info. If no auth
     *     info is present, the authorization in the metadata is left unchanged.
     */
    private static void decodeAuthInfo(
        final InterPartitionMessageDecoder messageDecoder, final RecordMetadata recordMetadata) {
      final var length = messageDecoder.authLength();
      if (length <= 0) {
        return;
      }
      final var offset = messageDecoder.limit() + InterPartitionMessageDecoder.authHeaderLength();
      recordMetadata.getAuthorization().wrap(messageDecoder.buffer(), offset, length);
      messageDecoder.skipAuth();
    }
  }
}
