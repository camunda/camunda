/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListenerImpl;
import io.camunda.zeebe.engine.transport.InterPartitionCommandSender;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

public class InterPartitionCommandReceiverTest {
  @Rule public final ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

  ClusterCommunicationService communicationService;
  private LogStreamRecordWriter logstream;

  @Before
  public void setup() {
    communicationService = mock(ClusterCommunicationService.class);
    logstream =
        mock(LogStreamRecordWriter.class, withSettings().defaultAnswer(Answers.RETURNS_SELF));
  }

  @Test
  public void shouldWriteSentCommandToLogStream() {
    // given
    final var receiverBrokerId = 1;
    final var receiverPartitionId = 3;

    final var sentMessage =
        sendCommand(
            receiverPartitionId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new byte[100]);

    final var receiver =
        new InterPartitionCommandReceiver(
            receiverBrokerId, receiverPartitionId, communicationService, logstream);
    scheduler.submitActor(receiver);
    scheduler.workUntilDone();

    // when
    getHandlerForPartition(communicationService, receiverPartitionId).accept(sentMessage);
    scheduler.workUntilDone();

    // then - sent message can be written to log stream
    verify(logstream).tryWrite();
  }

  @Test
  public void shouldNotWriteIfNoDiskSpaceAvailable() {
    // given
    final var receiverBrokerId = 3;
    final var receiverPartitionId = 5;

    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new byte[100]);

    final var receiver =
        new InterPartitionCommandReceiver(
            receiverBrokerId, receiverPartitionId, communicationService, logstream);

    scheduler.submitActor(receiver);
    scheduler.workUntilDone();

    // when
    receiver.onDiskSpaceNotAvailable();
    getHandlerForPartition(communicationService, receiverPartitionId).accept(sentMessage);
    scheduler.workUntilDone();

    // then
    verifyNoInteractions(logstream);
  }

  @Test
  public void writtenMetadataShouldBeCorrect() {
    // given
    final var receiverBrokerId = 1;
    final var receiverPartitionId = 5;

    final var valueType = ValueType.MESSAGE_SUBSCRIPTION;
    final var intent = MessageSubscriptionIntent.CORRELATE;

    final var sentMessage =
        sendCommand(receiverBrokerId, receiverPartitionId, valueType, intent, new byte[100]);

    final var receiver =
        new InterPartitionCommandReceiver(
            receiverBrokerId, receiverPartitionId, communicationService, logstream);

    scheduler.submitActor(receiver);
    scheduler.workUntilDone();

    // when
    getHandlerForPartition(communicationService, receiverPartitionId).accept(sentMessage);
    scheduler.workUntilDone();

    // then
    final var metadataCaptor = ArgumentCaptor.forClass(BufferWriter.class);
    verify(logstream).metadataWriter(metadataCaptor.capture());
    final var metadataWriter = metadataCaptor.getValue();
    final var metadataBuffer = new ExpandableArrayBuffer();
    final var metadata = new RecordMetadata();
    metadataWriter.write(metadataBuffer, 0);
    metadata.wrap(metadataBuffer, 0, metadataWriter.getLength());

    assertThat(metadata.getRecordType()).isEqualTo(RecordType.COMMAND);
    assertThat(metadata.getValueType()).isEqualTo(valueType);
    assertThat(metadata.getIntent()).isEqualTo(intent);
  }

  @Test
  public void shouldWriteGivenCommand() {
    // given
    final var receiverBrokerId = 3;
    final var receiverPartitionId = 5;

    final var commandBytes = new byte[100];

    // initialize to make debugging easier in case this test breaks.
    for (int i = 0; i < commandBytes.length; i++) {
      commandBytes[i] = (byte) i;
    }

    final var sentMessage =
        sendCommand(
            receiverBrokerId,
            receiverPartitionId,
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            commandBytes);

    final var receiver =
        new InterPartitionCommandReceiver(
            receiverBrokerId, receiverPartitionId, communicationService, logstream);

    scheduler.submitActor(receiver);
    scheduler.workUntilDone();

    // when
    getHandlerForPartition(communicationService, receiverPartitionId).accept(sentMessage);
    scheduler.workUntilDone();

    // then
    final var valueCaptor = ArgumentCaptor.forClass(BufferWriter.class);
    verify(logstream).valueWriter(valueCaptor.capture());
    final var valueWriter = valueCaptor.getValue();
    final var bytesWrittenToLogStream = new byte[valueWriter.getLength()];
    final var valueBuffer = new UnsafeBuffer(bytesWrittenToLogStream);
    valueWriter.write(valueBuffer, 0);
    valueWriter.getLength();

    assertThat(bytesWrittenToLogStream).isEqualTo(commandBytes);
  }

  private TopologyPartitionListenerImpl mockTopologyListener(
      final int partitionId, final int brokerId) {
    final var topologyListener = mock(TopologyPartitionListenerImpl.class);

    final var topology = new Int2IntHashMap(-1);
    topology.put(partitionId, brokerId);
    when(topologyListener.getPartitionLeaders()).thenReturn(topology);

    return topologyListener;
  }

  private Consumer<byte[]> getHandlerForPartition(
      final ClusterCommunicationService communicationService, final int partitionId) {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<BiConsumer<MemberId, byte[]>> handlerCaptor =
        ArgumentCaptor.forClass(BiConsumer.class);
    final var executorCaptor = ArgumentCaptor.forClass(Executor.class);
    verify(communicationService)
        .subscribe(
            eq(InterPartitionCommandSender.TOPIC_PREFIX + partitionId),
            handlerCaptor.capture(),
            executorCaptor.capture());
    return (byte[] bytes) ->
        executorCaptor
            .getValue()
            .execute(() -> handlerCaptor.getValue().accept(new MemberId("0"), bytes));
  }

  private byte[] sendCommand(
      final Integer receiverBrokerId,
      final Integer receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final byte[] command) {

    final var topologyListener = mockTopologyListener(receiverPartitionId, receiverBrokerId);
    final var sender = new InterPartitionCommandSenderImpl(communicationService, topologyListener);

    final var buffer = new UnsafeBuffer(command);
    final var bufferWriter = new DirectBufferWriter();
    bufferWriter.wrap(buffer);
    sender.sendCommand(receiverPartitionId, valueType, intent, bufferWriter);
    scheduler.workUntilDone();

    final var messageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(communicationService)
        .unicast(
            eq(InterPartitionCommandSender.TOPIC_PREFIX + receiverPartitionId),
            messageCaptor.capture(),
            any());

    return messageCaptor.getValue();
  }
}
