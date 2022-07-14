/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.clustering.management.InterPartitionMessageDecoder;
import io.camunda.zeebe.clustering.management.MessageHeaderDecoder;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class InterPartitionCommandReceiverImpl {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final Decoder decoder = new Decoder();
  private final LogStreamRecordWriter logStreamWriter;
  private boolean diskSpaceAvailable = true;

  public InterPartitionCommandReceiverImpl(final LogStreamRecordWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
  }

  public void handleMessage(final MemberId memberId, final byte[] message) {
    LOG.trace("Received message from {}", memberId);

    final var decoded = decoder.decodeMessage(message);

    if (!diskSpaceAvailable) {
      LOG.warn(
          "Ignoring command {} {} from {}, no disk space available",
          decoded.metadata.getValueType(),
          decoded.metadata.getIntent(),
          memberId);
      return;
    }

    logStreamWriter.reset();
    final var writeResult =
        logStreamWriter.metadataWriter(decoded.metadata).valueWriter(decoded.command).tryWrite();
    if (writeResult < 0) {
      LOG.warn(
          "Failed to write command {} {} from {} to logstream",
          decoded.metadata.getValueType(),
          decoded.metadata.getIntent(),
          memberId);
    }
  }

  public void setDiskSpaceAvailable(final boolean available) {
    diskSpaceAvailable = available;
  }

  private static final class Decoder {
    private final UnsafeBuffer messageBuffer = new UnsafeBuffer();
    private final RecordMetadata recordMetadata = new RecordMetadata();
    private final InterPartitionMessageDecoder messageDecoder = new InterPartitionMessageDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final DirectBufferWriter commandBuffer = new DirectBufferWriter();

    Decoder.DecodedMessage decodeMessage(final byte[] message) {
      messageBuffer.wrap(message);
      messageDecoder.wrapAndApplyHeader(messageBuffer, 0, headerDecoder);

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

      return new Decoder.DecodedMessage(recordMetadata, commandBuffer);
    }

    record DecodedMessage(RecordMetadata metadata, BufferWriter command) {}
  }
}
