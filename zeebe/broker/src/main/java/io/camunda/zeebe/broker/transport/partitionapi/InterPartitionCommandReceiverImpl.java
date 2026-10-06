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

    if (isNewCheckpoint(decoded)) {
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
                    memberId, decoded, error == null ? snapshotId : Optional.empty());
                return CompletableActorFuture.completed();
              },
              concurrencyControl);
    }

    writeCheckpointAndCommand(memberId, decoded, Optional.empty());
    return CompletableActorFuture.completed();
  }

  private void writeCheckpointAndCommand(
      final MemberId memberId, final DecodedMessage decoded, final Optional<String> snapshotId) {
    if (isNewCheckpoint(decoded)) {
      final var checkpointWritten = writeCheckpoint(decoded, snapshotId);
      if (checkpointWritten.isLeft()) {
        // It's unsafe to write this record without first writing the checkpoint, bail out early.
        logCheckpointFailure(memberId, decoded, checkpointWritten);
        return;
      }
    } else {
      releaseSnapshotReservation(decoded.checkpointId, snapshotId);
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

  private boolean isNewCheckpoint(final DecodedMessage decoded) {
    return decoded.checkpointId > Math.max(checkpointId, inFlightCheckpointId);
  }

  private void releaseSnapshotReservation(
      final long checkpointId, final Optional<String> snapshotId) {
    snapshotId.ifPresent(id -> snapshotStore.releaseReservation(checkpointId, id));
  }

  /**
   * Writes the checkpoint create record, which takes over the snapshot reservation. If the record
   * isn't written, the reservation is released, as nothing else would release it.
   */
  private Either<WriteFailure, Long> writeCheckpoint(
      final DecodedMessage decoded, final Optional<String> snapshotId) {
    LOG.debug(
        "Received command with checkpoint {}, current checkpoint is {}",
        decoded.checkpointId,
        checkpointId);
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(CheckpointIntent.CREATE)
            .valueType(ValueType.CHECKPOINT);
    final var checkpointRecord =
        new CheckpointRecord()
            .setCheckpointId(decoded.checkpointId)
            .setSnapshotId(snapshotId.orElse(""));
    final Either<WriteFailure, Long> written;
    try {
      written =
          logStreamWriter.tryWrite(
              WriteContext.interPartition(), LogAppendEntry.of(metadata, checkpointRecord));
    } catch (final RuntimeException e) {
      releaseSnapshotReservation(decoded.checkpointId, snapshotId);
      throw e;
    }

    if (written.isRight()) {
      inFlightCheckpointId = decoded.checkpointId;
    } else {
      releaseSnapshotReservation(decoded.checkpointId, snapshotId);
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

  void setCheckpointId(final long checkpointId) {
    this.checkpointId = checkpointId;
  }

  private record DecodedMessage(
      long checkpointId,
      Optional<Long> recordKey,
      RecordMetadata metadata,
      UnifiedRecordValue command) {}

  private static final class Decoder {
    private final InterPartitionMessageDecoder messageDecoder = new InterPartitionMessageDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    DecodedMessage decodeMessage(final byte[] message) {
      final var messageBuffer = new UnsafeBuffer();
      final var recordMetadata = new RecordMetadata();

      messageBuffer.wrap(message);
      messageDecoder.wrapAndApplyHeader(messageBuffer, 0, headerDecoder);

      final var checkpointId = messageDecoder.checkpointId();
      Optional<Long> recordKey = Optional.empty();
      if (messageDecoder.recordKey() != InterPartitionMessageDecoder.recordKeyNullValue()) {
        recordKey = Optional.of(messageDecoder.recordKey());
      }

      final var valueType = ValueType.get(messageDecoder.valueType());
      final var intent = Intent.fromProtocolValue(valueType, messageDecoder.intent());

      // rebuild the record metadata first, all messages must contain commands
      recordMetadata.reset().recordType(RecordType.COMMAND).valueType(valueType).intent(intent);

      // wrap the command buffer around the rest of the message
      // this does not try to parse the command, we are just assuming that these bytes
      // are a valid command
      final var commandOffset =
          messageDecoder.limit() + InterPartitionMessageDecoder.commandHeaderLength();
      final var commandLength = messageDecoder.commandLength();

      final var value = UnifiedRecordValue.fromValueType(valueType);
      if (value == null) {
        throw new IllegalArgumentException(
            "No value type mapped to %s, can't decode message".formatted(valueType));
      }

      value.wrap(messageBuffer, commandOffset, commandLength);
      return new DecodedMessage(checkpointId, recordKey, recordMetadata, value);
    }
  }
}
