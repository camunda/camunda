/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.clustering.management.InterPartitionMessageDecoder;
import io.camunda.zeebe.clustering.management.MessageHeaderDecoder;
import io.camunda.zeebe.engine.transport.InterPartitionCommandSender;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/**
 * Receives messages send by @{@link InterPartitionCommandSenderImpl} and tries to write them as
 * commands to the partition's log stream. Failure to write to the log stream, for example because
 * no disk space is available, the logstream rejected the write operation or message decoding
 * failure, are ignored. The sender is responsible for recognizing failures and retrying.
 */
public final class InterPartitionCommandReceiver extends Actor implements DiskSpaceUsageListener {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final String actorName;
  private final ClusterCommunicationService communicationService;
  private final LogStreamRecordWriter logStreamWriter;
  private final int partitionId;
  private final Decoder decoder = new Decoder();
  private boolean diskSpaceAvailable = true;

  public InterPartitionCommandReceiver(
      final int nodeId,
      final int partitionId,
      final ClusterCommunicationService communicationService,
      final LogStreamRecordWriter logStreamWriter) {
    this.partitionId = partitionId;
    this.communicationService = communicationService;
    this.logStreamWriter = logStreamWriter;
    actorName = buildActorName(nodeId, getClass().getSimpleName(), partitionId);
  }

  @Override
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    communicationService.subscribe(
        InterPartitionCommandSender.TOPIC_PREFIX + partitionId, this::tryHandleMessage, actor::run);
  }

  @Override
  protected void onActorClosing() {
    communicationService.unsubscribe(InterPartitionCommandSender.TOPIC_PREFIX + partitionId);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(() -> diskSpaceAvailable = false);
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(() -> diskSpaceAvailable = true);
  }

  private void tryHandleMessage(final MemberId memberId, final byte[] message) {
    try {
      handleMessage(memberId, message);
    } catch (final RuntimeException e) {
      LOG.error("Error while handling message", e);
    }
  }

  private void handleMessage(final MemberId memberId, final byte[] message) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Received message from {}", memberId.id());
    }

    if (!diskSpaceAvailable) {
      LOG.warn("Ignoring message, no disk space available");
      return;
    }

    final var decoded = decoder.decodeMessage(message);

    LOG.trace("Decoded command, now writing to logstream");
    logStreamWriter.reset();
    final var writeResult =
        logStreamWriter.metadataWriter(decoded.metadata).valueWriter(decoded.command).tryWrite();
    if (writeResult < 0) {
      LOG.warn("Failed to write command to logstream");
    } else {
      LOG.trace("Command written to logstream");
    }
  }

  private static final class Decoder {
    private final UnsafeBuffer messageBuffer = new UnsafeBuffer();
    private final RecordMetadata recordMetadata = new RecordMetadata();
    private final InterPartitionMessageDecoder messageDecoder = new InterPartitionMessageDecoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final DirectBufferWriter commandBuffer = new DirectBufferWriter();


    DecodedMessage decodeMessage(final byte[] message) {
      messageBuffer.wrap(message);
      messageDecoder.wrapAndApplyHeader(messageBuffer, 0, headerDecoder);

      final var valueType = ValueType.get(messageDecoder.valueType());
      final var intent = Intent.fromProtocolValue(valueType, messageDecoder.intent());

      // rebuild the record metadata first, all messages contain commands for the current
      // partitionId
      recordMetadata.reset().recordType(RecordType.COMMAND).valueType(valueType).intent(intent);

      // wrap the command buffer around the rest of the message
      // this does not try to parse the command, we are just assuming that these bytes
      // are a valid command
      final var commandOffset =
          messageDecoder.limit() + InterPartitionMessageDecoder.commandHeaderLength();
      final var commandLength = messageDecoder.commandLength();
      commandBuffer.wrap(messageBuffer, commandOffset, commandLength);

      return new DecodedMessage(recordMetadata, commandBuffer);
    }

    record DecodedMessage(BufferWriter metadata, BufferWriter command) {}
  }
}
