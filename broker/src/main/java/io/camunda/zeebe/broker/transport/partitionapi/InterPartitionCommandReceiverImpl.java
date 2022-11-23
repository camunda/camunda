/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.protocol.InterPartitionMessageDecoder;
import io.camunda.zeebe.broker.protocol.MessageHeaderDecoder;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

final class InterPartitionCommandReceiverImpl {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final Decoder decoder = new Decoder();
  private final LogStreamRecordWriter logStreamWriter;
  private final ConcurrencyControl executor;

  private boolean diskSpaceAvailable = true;
  private long checkpointId = CheckpointState.NO_CHECKPOINT;

  InterPartitionCommandReceiverImpl(
      final LogStreamRecordWriter logStreamWriter, final ConcurrencyControl executor) {
    this.logStreamWriter = logStreamWriter;
    this.executor = executor;
  }

  ActorFuture<Void> handleMessage(final MemberId memberId, final byte[] message) {
    final ActorFuture<Void> result = executor.createFuture();
    final DecodedMessage decoded;
    LOG.trace("Received message from {}", memberId);

    try {
      decoded = decoder.decodeMessage(message);
    } catch (final Exception e) {
      result.completeExceptionally(e);
      return result;
    }

    if (!diskSpaceAvailable) {
      result.completeExceptionally(
          new IllegalStateException(
              "Ignoring command %s %s from %s, checkpoint %d, no disk space available"
                  .formatted(
                      decoded.metadata.getValueType(),
                      decoded.metadata.getIntent(),
                      memberId,
                      decoded.checkpointId)));
      return result;
    }

    // in both cases (write checkpoint/command), ignore any errors, as there isn't anything to be
    // done with the error
    executor.runOnCompletion(
        writeCheckpoint(decoded),
        (ok, error) -> onCheckpointWritten(memberId, result, decoded, error));

    return result;
  }

  private void onCheckpointWritten(
      final MemberId memberId,
      final ActorFuture<Void> result,
      final DecodedMessage decoded,
      final Throwable writeError) {
    if (writeError != null) {
      result.completeExceptionally(
          new IllegalStateException(
              "Failed to write new command for checkpoint %d (currently at %d), ignoring command %s %s from %s"
                  .formatted(
                      decoded.checkpointId,
                      checkpointId,
                      decoded.metadata.getValueType(),
                      decoded.metadata.getIntent(),
                      memberId),
              writeError));
      return;
    }

    LOG.trace("Wrote checkpoint command {} from {}", decoded, memberId);
    executor.runOnCompletion(
        writeCommand(decoded), (ok, error) -> onCommandWritten(memberId, result, decoded, error));
  }

  private static void onCommandWritten(
      final MemberId memberId,
      final ActorFuture<Void> result,
      final DecodedMessage decoded,
      final Throwable error) {
    if (error != null) {
      result.completeExceptionally(
          new IllegalStateException(
              "Failed to write command %s %s from %s to logstream"
                  .formatted(
                      decoded.metadata.getValueType(), decoded.metadata.getIntent(), memberId),
              error));
      return;
    }

    LOG.trace("Wrote remote command {} from {}", decoded, memberId);
    result.complete(null);
  }

  private ActorFuture<Void> writeCheckpoint(final DecodedMessage decoded) {
    final ActorFuture<Void> result = executor.createFuture();
    if (decoded.checkpointId <= checkpointId) {
      // No need to write a new checkpoint create record
      result.complete(null);
      return result;
    }

    LOG.debug(
        "Received command with checkpoint {}, current checkpoint is {}",
        decoded.checkpointId,
        checkpointId);
    logStreamWriter.reset();
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(CheckpointIntent.CREATE)
            .valueType(ValueType.CHECKPOINT);
    final var checkpointRecord = new CheckpointRecord().setCheckpointId(decoded.checkpointId);
    final var writeResult =
        logStreamWriter.metadataWriter(metadata).valueWriter(checkpointRecord).tryWrite();
    executor.runOnCompletion(
        writeResult,
        (ok, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(null);
          }
        });

    return result;
  }

  private ActorFuture<Void> writeCommand(final DecodedMessage decoded) {
    final ActorFuture<Void> result = executor.createFuture();
    logStreamWriter.reset();
    decoded.recordKey.ifPresent(logStreamWriter::key);
    final var writeResult =
        logStreamWriter.metadataWriter(decoded.metadata).valueWriter(decoded.command).tryWrite();
    executor.runOnCompletion(
        writeResult,
        (ignored, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(null);
          }
        });

    return result;
  }

  void setDiskSpaceAvailable(final boolean available) {
    diskSpaceAvailable = available;
  }

  void setCheckpointId(final long checkpointId) {
    this.checkpointId = checkpointId;
  }

  private record DecodedMessage(
      long checkpointId, Optional<Long> recordKey, RecordMetadata metadata, BufferWriter command) {}

  private static final class Decoder {
    private final UnsafeBuffer messageBuffer = new UnsafeBuffer();
    private final RecordMetadata recordMetadata = new RecordMetadata();
    private final InterPartitionMessageDecoder messageDecoder = new InterPartitionMessageDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final DirectBufferWriter commandBuffer = new DirectBufferWriter();

    DecodedMessage decodeMessage(final byte[] message) {
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
      commandBuffer.wrap(messageBuffer, commandOffset, commandLength);

      return new DecodedMessage(checkpointId, recordKey, recordMetadata, commandBuffer);
    }
  }
}
