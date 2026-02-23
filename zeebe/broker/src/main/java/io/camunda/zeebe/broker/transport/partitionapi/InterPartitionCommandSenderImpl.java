/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.utils.serializer.serializers.DefaultSerializers;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.protocol.InterPartitionMessageEncoder;
import io.camunda.zeebe.broker.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class InterPartitionCommandSenderImpl implements InterPartitionCommandSender {

  public static final String LEGACY_TOPIC_PREFIX = "inter-partition-";
  public static final String TOPIC_PREFIX = "%s-inter-partition-";

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final ClusterCommunicationService communicationService;

  private final Int2IntHashMap partitionLeaders = new Int2IntHashMap(-1);
  private long checkpointId = CheckpointState.NO_CHECKPOINT;
  private CheckpointType checkpointType = CheckpointType.MANUAL_BACKUP;
  private final String sendingSubjectPrefix;

  public InterPartitionCommandSenderImpl(
      final ClusterCommunicationService communicationService, final String sendingSubjectPrefix) {
    this.communicationService = communicationService;
    this.sendingSubjectPrefix = sendingSubjectPrefix;
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
    sendCommand(receiverPartitionId, valueType, intent, recordKey, command, null);
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command,
      final AuthInfo authInfo) {
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
        Encoder.encode(
            checkpointId,
            checkpointType,
            receiverPartitionId,
            valueType,
            intent,
            recordKey,
            command,
            authInfo);

    communicationService.unicast(
        sendingSubjectPrefix + receiverPartitionId,
        message,
        DefaultSerializers.BASIC::encode,
        MemberId.from("" + partitionLeader),
        true);
  }

  void setCheckpointInfo(final long checkpointId, final CheckpointType checkpointType) {
    this.checkpointId = checkpointId;
    this.checkpointType = checkpointType;
  }

  void setCurrentLeader(final int partitionId, final int currentLeader) {
    partitionLeaders.put(partitionId, currentLeader);
  }

  private static final class Encoder {

    private static byte[] encode(
        final long checkpointId,
        final CheckpointType checkpointType,
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final BufferWriter command,
        final BufferWriter authInfo) {
      final var commandLength = command.getLength();
      final var authInfoLength = authInfo != null ? authInfo.getLength() : 0;
      final var messageLength =
          MessageHeaderEncoder.ENCODED_LENGTH
              + InterPartitionMessageEncoder.BLOCK_LENGTH
              + InterPartitionMessageEncoder.commandHeaderLength()
              + commandLength
              + InterPartitionMessageEncoder.authHeaderLength()
              + authInfoLength;

      final var headerEncoder = new MessageHeaderEncoder();
      final var bodyEncoder = new InterPartitionMessageEncoder();
      final var commandBuffer = new UnsafeBuffer(new byte[commandLength]);
      final var messageBuffer = new UnsafeBuffer(new byte[messageLength]);
      command.write(commandBuffer, 0);
      bodyEncoder
          .wrapAndApplyHeader(messageBuffer, 0, headerEncoder)
          .checkpointId(checkpointId)
          .checkpointType(checkpointType.getValue())
          .receiverPartitionId(receiverPartitionId)
          .valueType(valueType.value())
          .intent(intent.value())
          .putCommand(commandBuffer, 0, commandLength);

      if (authInfo != null) {
        final var authBuffer = new UnsafeBuffer(new byte[authInfoLength]);
        authInfo.write(authBuffer, 0);
        bodyEncoder.putAuth(authBuffer, 0, authInfoLength);
      }

      bodyEncoder.recordKey(
          Objects.requireNonNullElseGet(
              recordKey, InterPartitionMessageEncoder::recordKeyNullValue));

      return messageBuffer.byteArray();
    }
  }
}
