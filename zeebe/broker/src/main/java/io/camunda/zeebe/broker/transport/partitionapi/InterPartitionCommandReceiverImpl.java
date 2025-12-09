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
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

final class InterPartitionCommandReceiverImpl {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final Decoder decoder = new Decoder();
  private final LogStreamWriter logStreamWriter;
  private boolean diskSpaceAvailable = true;
  private long checkpointId = CheckpointState.NO_CHECKPOINT;

  InterPartitionCommandReceiverImpl(final LogStreamWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
  }

  void handleMessage(final MemberId memberId, final byte[] message) {
    LOG.trace("Received message from {}", memberId);

    final var decoded = decoder.decodeMessage(message);

    if (!diskSpaceAvailable) {
      LOG.warn(
          "Ignoring command {} {} from {}, checkpoint {}, no disk space available",
          decoded.metadata.getValueType(),
          decoded.metadata.getIntent(),
          memberId,
          decoded.checkpointId);
      return;
    }

    final var checkpointWritten = writeCheckpoint(decoded);
    if (checkpointWritten.isLeft()) {
      // It's unsafe to write this record without first writing the checkpoint, bail out early.
      logCheckpointFailure(memberId, decoded, checkpointWritten);
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
    LOG.warn(
        "Failed to write command {} {} from {} to logstream (error = {})",
        decoded.metadata.getValueType(),
        decoded.metadata.getIntent(),
        memberId,
        failure);
  }

  private Either<WriteFailure, Long> writeCheckpoint(final DecodedMessage decoded) {
    if (decoded.checkpointId <= checkpointId) {
      // No need to write a new checkpoint create record
      return Either.right(checkpointId);
    }

    LOG.debug(
        "Received command with checkpoint {}, current checkpoint is {}",
        decoded.checkpointId,
        checkpointId);
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(CheckpointIntent.CREATE)
            .valueType(ValueType.CHECKPOINT);
    final var checkpointRecord = new CheckpointRecord().setCheckpointId(decoded.checkpointId);
    return logStreamWriter.tryWrite(
        WriteContext.interPartition(), LogAppendEntry.of(metadata, checkpointRecord));
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
