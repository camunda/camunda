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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListenerImpl;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;
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
  private final LogStreamRecordWriter logStreamRecordWriter;
  private final InterPartitionCommandSenderImpl sender;
  private final InterPartitionCommandReceiverImpl receiver;

  InterPartitionCommandCheckpointTest(
      @Mock final ClusterCommunicationService communicationService,
      @Mock(answer = Answers.RETURNS_SELF) final LogStreamRecordWriter logStreamRecordWriter) {
    this.communicationService = communicationService;
    this.logStreamRecordWriter = logStreamRecordWriter;

    final var topology = new Int2IntHashMap(-1);
    topology.put(1, 2);
    final var topologyListener = mock(TopologyPartitionListenerImpl.class);
    when(topologyListener.getPartitionLeaders()).thenReturn(topology);

    sender = new InterPartitionCommandSenderImpl(communicationService, topologyListener);
    receiver = new InterPartitionCommandReceiverImpl(logStreamRecordWriter);
  }

  @Test
  void shouldHandleMissingCheckpoints() {
    // given
    when(logStreamRecordWriter.tryWrite()).thenReturn(1L);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then

    final var io = inOrder(logStreamRecordWriter);
    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    io.verify(logStreamRecordWriter).tryWrite();
    io.verifyNoMoreInteractions();
  }

  @Test
  void shouldCreateFirstCheckpoint() {
    // given
    when(logStreamRecordWriter.tryWrite()).thenReturn(1L);
    sender.onNewCheckpointCreated(1, 10);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamRecordWriter);
    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.CHECKPOINT, CheckpointIntent.CREATE));
    io.verify(logStreamRecordWriter).valueWriter(matchCheckpoint(1));
    io.verify(logStreamRecordWriter).tryWrite();

    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    io.verify(logStreamRecordWriter).tryWrite();
    io.verifyNoMoreInteractions();
  }

  @Test
  void shouldUpdateExistingCheckpoint() {
    // given
    when(logStreamRecordWriter.tryWrite()).thenReturn(1L);
    receiver.setCheckpointId(5);
    sender.onNewCheckpointCreated(17, 10);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamRecordWriter);
    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.CHECKPOINT, CheckpointIntent.CREATE));
    io.verify(logStreamRecordWriter).valueWriter(matchCheckpoint(17));
    io.verify(logStreamRecordWriter).tryWrite();
    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    io.verify(logStreamRecordWriter).tryWrite();
  }

  @Test
  void shouldNotRecreateExistingCheckpoint() {
    // given
    when(logStreamRecordWriter.tryWrite()).thenReturn(1L);
    receiver.setCheckpointId(5);
    sender.onNewCheckpointCreated(5, 10);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamRecordWriter);
    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    io.verify(logStreamRecordWriter).tryWrite();
    io.verifyNoMoreInteractions();
  }

  @Test
  void shouldNotOverwriteNewerCheckpoint() {
    // given
    when(logStreamRecordWriter.tryWrite()).thenReturn(1L);
    receiver.setCheckpointId(6);
    sender.onNewCheckpointCreated(5, 10);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamRecordWriter);
    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    io.verify(logStreamRecordWriter).tryWrite();
    io.verifyNoMoreInteractions();
  }

  @Test
  void shouldNotWriteCommandIfCheckpointCreateFailed() {
    // given
    when(logStreamRecordWriter.tryWrite()).thenReturn(-1L, 1L);
    receiver.setCheckpointId(5);
    sender.onNewCheckpointCreated(17, 10);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamRecordWriter);
    io.verify(logStreamRecordWriter)
        .metadataWriter(matchMetadata(ValueType.CHECKPOINT, CheckpointIntent.CREATE));
    io.verify(logStreamRecordWriter).tryWrite();
    io.verifyNoMoreInteractions();
  }

  @Test
  void shouldNotWriteCommandIfNoDiskAvailable() {
    // given
    receiver.setDiskSpaceAvailable(false);
    receiver.setCheckpointId(5);
    sender.onNewCheckpointCreated(17, 10);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verifyNoInteractions(logStreamRecordWriter);
  }

  private BufferWriter matchMetadata(final ValueType valueType, final Intent intent) {
    return Mockito.argThat(
        metadataWriter -> {
          final var metadata = (RecordMetadata) metadataWriter;
          return metadata.getValueType() == valueType && metadata.getIntent() == intent;
        });
  }

  private BufferWriter matchCheckpoint(final long checkpointId) {
    return Mockito.argThat(
        valueWriter -> {
          if (valueWriter instanceof CheckpointRecord checkpoint) {
            return checkpoint.getCheckpointId() == checkpointId;
          } else {
            return false;
          }
        });
  }

  private void sendAndReceive(final ValueType valueType, final Intent intent) {
    final var bufferWriter = new DirectBufferWriter();
    bufferWriter.wrap(new UnsafeBuffer(new byte[100]));
    sender.sendCommand(1, valueType, intent, bufferWriter);

    final var messageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(communicationService).unicast(eq(TOPIC_PREFIX + 1), messageCaptor.capture(), any());
    receiver.handleMessage(new MemberId("0"), messageCaptor.getValue());
  }
}
