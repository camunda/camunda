/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderImpl.TOPIC_PREFIX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.util.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class InterPartitionCommandCheckpointTest {

  private final ClusterCommunicationService communicationService;
  private final LogStreamWriter logStreamWriter;
  private final InterPartitionCommandSenderImpl sender;
  private final InterPartitionCommandReceiverImpl receiver;

  InterPartitionCommandCheckpointTest(
      @Mock final ClusterCommunicationService communicationService,
      @Mock(answer = Answers.RETURNS_SELF) final LogStreamWriter logStreamWriter) {
    this.communicationService = communicationService;
    this.logStreamWriter = logStreamWriter;

    sender = new InterPartitionCommandSenderImpl(communicationService);
    sender.setCurrentLeader(1, 2);
    receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);
  }

  @Test
  void shouldHandleMissingCheckpoints() {
    // given
    when(logStreamWriter.tryWrite(Mockito.<LogAppendEntry>any())).thenReturn(Either.right(1L));

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter, times(1))
        .tryWrite(matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
  }

  @Test
  void shouldCreateFirstCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(Mockito.<LogAppendEntry>any())).thenReturn(Either.right(1L));
    sender.setCheckpointId(1);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamWriter);
    io.verify(logStreamWriter, times(1)).tryWrite(matchesCheckpoint(1));
    io.verify(logStreamWriter, times(1))
        .tryWrite(matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    io.verifyNoMoreInteractions();
  }

  @Test
  void shouldUpdateExistingCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(Mockito.<LogAppendEntry>any())).thenReturn(Either.right(1L));
    receiver.setCheckpointId(5);
    sender.setCheckpointId(17);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamWriter);
    io.verify(logStreamWriter).tryWrite(matchesCheckpoint(17));
    io.verify(logStreamWriter)
        .tryWrite(matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
  }

  @Test
  void shouldNotRecreateExistingCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(Mockito.<LogAppendEntry>any())).thenReturn(Either.right(1L));
    receiver.setCheckpointId(5);
    sender.setCheckpointId(5);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter)
        .tryWrite(matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
  }

  @Test
  void shouldNotOverwriteNewerCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(Mockito.<LogAppendEntry>any())).thenReturn(Either.right(1L));
    receiver.setCheckpointId(6);
    sender.setCheckpointId(5);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter)
        .tryWrite(matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
  }

  @Test
  void shouldNotWriteCommandIfCheckpointCreateFailed() {
    // given
    when(logStreamWriter.tryWrite(Mockito.<LogAppendEntry>any()))
        .thenReturn(Either.left(WriteFailure.FULL), Either.right(1L));
    receiver.setCheckpointId(5);
    sender.setCheckpointId(17);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter)
        .tryWrite(matchesMetadata(ValueType.CHECKPOINT, CheckpointIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
  }

  @Test
  void shouldNotWriteCommandIfNoDiskAvailable() {
    // given
    receiver.setDiskSpaceAvailable(false);
    receiver.setCheckpointId(5);
    sender.setCheckpointId(17);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verifyNoInteractions(logStreamWriter);
  }

  private LogAppendEntry matchesMetadata(final ValueType valueType, final Intent intent) {
    return Mockito.argThat(entry -> matchesMetadata(entry, valueType, intent));
  }

  private boolean matchesMetadata(
      final LogAppendEntry entry, final ValueType valueType, final Intent intent) {
    final var metadata = (RecordMetadata) entry.recordMetadata();
    return metadata.getValueType() == valueType && metadata.getIntent() == intent;
  }

  private LogAppendEntry matchesCheckpoint(final long checkpointId) {
    return Mockito.argThat(
        entry ->
            matchesMetadata(entry, ValueType.CHECKPOINT, CheckpointIntent.CREATE)
                && entry.recordValue() instanceof CheckpointRecord checkpoint
                && checkpoint.getCheckpointId() == checkpointId);
  }

  private void sendAndReceive(final ValueType valueType, final Intent intent) {
    sender.sendCommand(1, valueType, intent, new JobRecord());

    final var messageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(communicationService)
        .unicast(eq(TOPIC_PREFIX + 1), messageCaptor.capture(), any(), any(), eq(true));
    receiver.handleMessage(new MemberId("0"), messageCaptor.getValue());
  }
}
