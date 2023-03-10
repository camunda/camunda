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
import io.atomix.utils.serializer.serializers.DefaultSerializers;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.protocol.InterPartitionMessageEncoder;
import io.camunda.zeebe.broker.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

final class InterPartitionCommandSenderImpl implements InterPartitionCommandSender {

  public static final String TOPIC_PREFIX = "inter-partition-";

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final ClusterCommunicationService communicationService;

  private final Int2IntHashMap partitionLeaders = new Int2IntHashMap(-1);
  private long checkpointId = CheckpointState.NO_CHECKPOINT;

  public InterPartitionCommandSenderImpl(final ClusterCommunicationService communicationService) {
    this.communicationService = communicationService;
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue command) {
    sendCommand(receiverPartitionId, valueType, intent, null, command);
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command) {
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
        TOPIC_PREFIX + receiverPartitionId,
        message,
        DefaultSerializers.BASIC::encode,
        MemberId.from("" + partitionLeader),
        true);
  }

  void setCheckpointId(final long checkpointId) {
    this.checkpointId = checkpointId;
  }

  void setCurrentLeader(final int partitionId, final int currentLeader) {
    partitionLeaders.put(partitionId, currentLeader);
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
