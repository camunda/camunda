/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderImpl.TOPIC_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@Execution(ExecutionMode.CONCURRENT)
final class InterPartitionCommandReceiverTest {

  @Test
  void shouldWriteSentCommandToLogStream() {
    // given
    final var receiverBrokerId = 1;
    final var receiverPartitionId = 3;

    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new MessageSubscriptionRecord().setProcessInstanceKey(1).setElementInstanceKey(1));

    final var logStreamWriter =
        mock(LogStreamWriter.class, withSettings().defaultAnswer(Answers.RETURNS_SELF));
    final var receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);

    // when
    receiver.handleMessage(new MemberId("0"), sentMessage);

    // then - sent message can be written to log stream
    verify(logStreamWriter).tryWrite(Mockito.<LogAppendEntry>any());
  }

  @Test
  void shouldNotWriteIfNoDiskSpaceAvailable() {
    // given
    final var receiverBrokerId = 3;
    final var receiverPartitionId = 5;

    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new MessageSubscriptionRecord().setProcessInstanceKey(1).setElementInstanceKey(1));

    final var logStreamWriter =
        mock(LogStreamWriter.class, withSettings().defaultAnswer(Answers.RETURNS_SELF));
    final var receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);

    // when
    receiver.setDiskSpaceAvailable(false);
    receiver.handleMessage(new MemberId("0"), sentMessage);

    // then
    verifyNoInteractions(logStreamWriter);
  }

  @Test
  void writtenMetadataShouldBeCorrect() {
    // given
    final var receiverBrokerId = 1;
    final var receiverPartitionId = 5;

    final var valueType = ValueType.MESSAGE_SUBSCRIPTION;
    final var intent = MessageSubscriptionIntent.CORRELATE;

    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            valueType,
            intent,
            new MessageSubscriptionRecord().setProcessInstanceKey(1).setElementInstanceKey(1));

    final var logStreamWriter =
        mock(LogStreamWriter.class, withSettings().defaultAnswer(Answers.RETURNS_SELF));
    final var receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);

    // when
    receiver.handleMessage(new MemberId("0"), sentMessage);

    // then
    final var entryCaptor = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter).tryWrite(entryCaptor.capture());
    final var metadataWriter = entryCaptor.getValue().recordMetadata();
    final var metadataBuffer = new ExpandableArrayBuffer();
    final var metadata = new RecordMetadata();
    metadataWriter.write(metadataBuffer, 0);
    metadata.wrap(metadataBuffer, 0, metadataWriter.getLength());

    assertThat(metadata.getRecordType()).isEqualTo(RecordType.COMMAND);
    assertThat(metadata.getValueType()).isEqualTo(valueType);
    assertThat(metadata.getIntent()).isEqualTo(intent);
  }

  @Test
  void shouldWriteGivenCommand() {
    // given
    final var receiverBrokerId = 3;
    final var receiverPartitionId = 5;

    // initialize to make debugging easier in case this test breaks.
    final var recordValue =
        new MessageSubscriptionRecord().setProcessInstanceKey(1).setElementInstanceKey(1);
    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            recordValue);

    final var logStreamWriter =
        mock(LogStreamWriter.class, withSettings().defaultAnswer(Answers.RETURNS_SELF));
    final var receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);

    // when
    receiver.handleMessage(new MemberId("0"), sentMessage);

    // then
    final var entryCaptor = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter).tryWrite(entryCaptor.capture());
    final var valueWriter = entryCaptor.getValue().recordValue();
    assertThat(valueWriter).isEqualTo(recordValue);
  }

  @Test
  void shouldWriteCommandWithRecordKey() {
    // given
    final var receiverBrokerId = 3;
    final var receiverPartitionId = 5;

    final var recordKey = 10L;

    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            recordKey,
            new MessageSubscriptionRecord().setProcessInstanceKey(1).setElementInstanceKey(1));

    final var logStreamWriter =
        mock(LogStreamWriter.class, withSettings().defaultAnswer(Answers.RETURNS_SELF));
    final var receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);
    final var entryCaptor = ArgumentCaptor.forClass(LogAppendEntry.class);

    // when
    receiver.handleMessage(new MemberId("0"), sentMessage);

    // then
    verify(logStreamWriter).tryWrite(entryCaptor.capture());
    assertThat(entryCaptor.getValue().key()).isEqualTo(recordKey);
  }

  @Test
  void shouldWriteCommandWithoutKey() {
    // given
    final var receiverBrokerId = 3;
    final var receiverPartitionId = 5;

    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new MessageSubscriptionRecord().setProcessInstanceKey(1).setElementInstanceKey(1));

    final var logStreamWriter =
        mock(LogStreamWriter.class, withSettings().defaultAnswer(Answers.RETURNS_SELF));
    final var receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);
    final var entryCaptor = ArgumentCaptor.forClass(LogAppendEntry.class);

    // when
    receiver.handleMessage(new MemberId("0"), sentMessage);

    // then
    verify(logStreamWriter).tryWrite(entryCaptor.capture());
    assertThat(entryCaptor.getValue().key()).isEqualTo(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  private byte[] sendCommand(
      final Integer receiverBrokerId,
      final Integer receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue recordValue) {
    return sendCommand(receiverBrokerId, receiverPartitionId, valueType, intent, null, recordValue);
  }

  private byte[] sendCommand(
      final Integer receiverBrokerId,
      final Integer receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue recordValue) {
    final ClusterCommunicationService communicationService =
        mock(ClusterCommunicationService.class);

    final var sender = new InterPartitionCommandSenderImpl(communicationService);
    sender.setCurrentLeader(receiverPartitionId, receiverBrokerId);

    sender.sendCommand(receiverPartitionId, valueType, intent, recordKey, recordValue);

    final var messageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(communicationService)
        .unicast(
            eq(TOPIC_PREFIX + receiverPartitionId),
            messageCaptor.capture(),
            any(),
            any(),
            eq(true));

    return messageCaptor.getValue();
  }
}
