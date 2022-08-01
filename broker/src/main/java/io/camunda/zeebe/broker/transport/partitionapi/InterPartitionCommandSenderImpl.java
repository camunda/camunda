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
import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListenerImpl;
import io.camunda.zeebe.clustering.management.InterPartitionMessageEncoder;
import io.camunda.zeebe.clustering.management.MessageHeaderEncoder;
import io.camunda.zeebe.engine.transport.InterPartitionCommandSender;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class InterPartitionCommandSenderImpl
    implements InterPartitionCommandSender, CheckpointListener {
  public static final String TOPIC_PREFIX = "inter-partition-";
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final ClusterCommunicationService communicationService;

  private final TopologyPartitionListenerImpl partitionListener;
  private long checkpointId = CheckpointState.NO_CHECKPOINT;

  public InterPartitionCommandSenderImpl(
      final ClusterCommunicationService communicationService,
      final TopologyPartitionListenerImpl partitionListener) {
    this.communicationService = communicationService;
    this.partitionListener = partitionListener;
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final BufferWriter command) {
    sendCommand(receiverPartitionId, valueType, intent, null, command);
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final BufferWriter command) {
    final var partitionLeaders = partitionListener.getPartitionLeaders();
    if (!partitionLeaders.containsKey(receiverPartitionId)) {
      LOG.warn(
          "Not sending command {} {} to {}, no known leader for this partition",
          valueType,
          intent,
          receiverPartitionId);
      return;
    }
    final int partitionLeader = partitionLeaders.get(receiverPartitionId);

    LOG.trace(
        "Sending command {} {} to partition {}, leader {}",
        valueType,
        intent,
        receiverPartitionId,
        partitionLeader);

    final var message =
        Encoder.encode(checkpointId, receiverPartitionId, valueType, intent, recordKey, command);

    communicationService.unicast(
        TOPIC_PREFIX + receiverPartitionId, message, MemberId.from("" + partitionLeader));
  }

  @Override
  public void onNewCheckpointCreated(final long checkpointId) {
    this.checkpointId = checkpointId;
  }

  private static final class Encoder {

    private static byte[] encode(
        final long checkpointId,
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final BufferWriter command) {
      final var messageLength =
          MessageHeaderEncoder.ENCODED_LENGTH
              + InterPartitionMessageEncoder.BLOCK_LENGTH
              + InterPartitionMessageEncoder.commandHeaderLength()
              + command.getLength();

      final var headerEncoder = new MessageHeaderEncoder();
      final var bodyEncoder = new InterPartitionMessageEncoder();
      final var commandBuffer = new UnsafeBuffer(new byte[command.getLength()]);
      final var messageBuffer = new UnsafeBuffer(new byte[messageLength]);
      command.write(commandBuffer, 0);
      bodyEncoder
          .wrapAndApplyHeader(messageBuffer, 0, headerEncoder)
          .checkpointId(checkpointId)
          .receiverPartitionId(receiverPartitionId)
          .valueType(valueType.value())
          .intent(intent.value())
          .putCommand(commandBuffer, 0, command.getLength());

      bodyEncoder.recordKey(
          Objects.requireNonNullElseGet(
              recordKey, InterPartitionMessageEncoder::recordKeyNullValue));

      return messageBuffer.byteArray();
    }
  }
}
