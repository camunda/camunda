/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.common.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.common.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.common.state.immutable.DistributionState;
import io.camunda.zeebe.engine.common.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * This test differs from most other tests in the engine module. It is a unit test for the
 * CommandDistributionBehavior class and does not use the EngineRule to start the engine. Instead,
 * this tests the behavior of the CommandDistributionBehavior in isolation. This is necessary
 * because the CommandDistributionBehavior is otherwise hard to test.
 *
 * <p>Typically, these tests assert that the behavior correctly appends follow-up records to the
 * ProcessingResultBuilder, and that it correctly sends commands to other partitions through the
 * InterPartitionCommandSender.
 *
 * <p>The command that is distributed in these tests is always the same, because the behavior is
 * independent of the type of command, although slight coupling exists to the value type.
 */
class CommandDistributionBehaviorTest {

  private DistributionState mockDistributionState;
  private DistributionMetrics mockDistributionMetrics;
  private FakeProcessingResultBuilder<CommandDistributionRecord> fakeProcessingResultBuilder;
  private InterPartitionCommandSender mockInterpartitionCommandSender;
  private Writers writers;

  private long key;
  private ValueType valueType;
  private DeploymentIntent intent;
  private MockTypedRecord<DeploymentRecord> command;

  @BeforeEach
  void setUp() {
    mockDistributionMetrics = mock(DistributionMetrics.class);
    mockDistributionState = mock(DistributionState.class);
    fakeProcessingResultBuilder = new FakeProcessingResultBuilder<>();
    mockInterpartitionCommandSender = mock(InterPartitionCommandSender.class);
    writers = new Writers(() -> fakeProcessingResultBuilder, mock(EventAppliers.class));

    key = Protocol.encodePartitionId(1, 100);
    valueType = ValueType.DEPLOYMENT;
    intent = DeploymentIntent.CREATE;
    command =
        new MockTypedRecord<>(
            key, new RecordMetadata().valueType(valueType).intent(intent), new DeploymentRecord());
  }

  @Test
  void shouldNotDistributeCommandToThisPartition() {
    // given 1 partition
    final var behavior =
        new CommandDistributionBehavior(
            mockDistributionState,
            writers,
            1,
            RoutingInfo.forStaticPartitions(1),
            mockInterpartitionCommandSender,
            mockDistributionMetrics);

    // when distributing to all partitions
    behavior.withKey(key).unordered().distribute(command);

    // then no command distribution is started
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords()).isEmpty();

    // then no command is sent to other partitions
    fakeProcessingResultBuilder.flushPostCommitTasks();
    verifyNoInteractions(mockInterpartitionCommandSender);
  }

  @Test
  void shouldDistributeCommandToAllOtherPartitions() {
    // given 3 partitions and behavior on partition 1
    final var behavior =
        new CommandDistributionBehavior(
            mockDistributionState,
            writers,
            1,
            RoutingInfo.forStaticPartitions(3),
            mockInterpartitionCommandSender,
            mockDistributionMetrics);

    // when distributing to all partitions
    behavior.withKey(key).unordered().distribute(command);

    // then command distribution is started on partition 1 and distributing to all other partitions
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .extracting(
            Record::getKey,
            Record::getIntent,
            r -> r.getValue().getPartitionId(),
            r -> r.getValue().getIntent())
        .hasSize(3)
        .startsWith(tuple(key, CommandDistributionIntent.STARTED, 1, intent))
        .contains(
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 2, intent),
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 3, intent));

    // then command is sent to all other partitions
    fakeProcessingResultBuilder.flushPostCommitTasks();
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(key), any());
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(key), any());
    verifyNoMoreInteractions(mockInterpartitionCommandSender);
  }

  @Test
  void shouldDistributeCommandToSpecificPartitions() {
    // given 4 partitions and behavior on partition 2
    final var behavior =
        new CommandDistributionBehavior(
            mockDistributionState,
            writers,
            2,
            RoutingInfo.forStaticPartitions(4),
            mockInterpartitionCommandSender,
            mockDistributionMetrics);

    // when distributing to partitions 1 and 3
    behavior.withKey(key).unordered().forPartitions(Set.of(1, 3)).distribute(command);

    // then command distribution is started on partition 2 and distributing to all other partitions
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .extracting(
            Record::getKey,
            Record::getIntent,
            r -> r.getValue().getPartitionId(),
            r -> r.getValue().getIntent())
        .hasSize(3)
        .startsWith(tuple(key, CommandDistributionIntent.STARTED, 2, intent))
        .contains(
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 1, intent),
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 3, intent));

    // then command is sent to partitions 1 and 3
    fakeProcessingResultBuilder.flushPostCommitTasks();
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(1), eq(valueType), eq(intent), eq(key), any());
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(key), any());
    verifyNoMoreInteractions(mockInterpartitionCommandSender);
  }

  @Test
  void shouldStartQueueImmediately() {
    // given 3 partitions and behavior on partition 1
    final var behavior =
        new CommandDistributionBehavior(
            mockDistributionState,
            writers,
            1,
            RoutingInfo.forStaticPartitions(3),
            mockInterpartitionCommandSender,
            mockDistributionMetrics);

    // when distributing first command in queue to all partitions
    behavior.withKey(key).inQueue("test-queue").distribute(command);

    // then command distribution is started on partition 1, distribution is enqueued and triggered
    // immediately for partitions 2 and 3
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
        .containsExactly(
            tuple(key, CommandDistributionIntent.STARTED, 1),
            tuple(key, CommandDistributionIntent.ENQUEUED, 2),
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 2),
            tuple(key, CommandDistributionIntent.ENQUEUED, 3),
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 3));

    // then command is sent immediately to partitions 2 and 3
    fakeProcessingResultBuilder.flushPostCommitTasks();
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(key), any());
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(key), any());
    verifyNoMoreInteractions(mockInterpartitionCommandSender);
  }

  @Test
  void shouldWaitInQueue() {
    // given 3 partitions and behavior on partition 1
    final var behavior =
        new CommandDistributionBehavior(
            mockDistributionState,
            writers,
            1,
            RoutingInfo.forStaticPartitions(3),
            mockInterpartitionCommandSender,
            mockDistributionMetrics);

    final var firstKey = Protocol.encodePartitionId(1, 100);
    final var secondKey = Protocol.encodePartitionId(1, 101);

    // when adding two distributions to the same queue
    behavior.withKey(firstKey).inQueue("test-queue").distribute(command);
    when(mockDistributionState.getNextQueuedDistributionKey("test-queue", 2))
        .thenReturn(Optional.of(firstKey));
    when(mockDistributionState.getNextQueuedDistributionKey("test-queue", 3))
        .thenReturn(Optional.of(firstKey));
    behavior.withKey(secondKey).inQueue("test-queue").distribute(command);

    // then first distribution is triggered immediately and second distribution is enqueued
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
        .containsExactly(
            tuple(firstKey, CommandDistributionIntent.STARTED, 1),
            tuple(firstKey, CommandDistributionIntent.ENQUEUED, 2),
            tuple(firstKey, CommandDistributionIntent.DISTRIBUTING, 2),
            tuple(firstKey, CommandDistributionIntent.ENQUEUED, 3),
            tuple(firstKey, CommandDistributionIntent.DISTRIBUTING, 3),
            tuple(secondKey, CommandDistributionIntent.STARTED, 1),
            tuple(secondKey, CommandDistributionIntent.ENQUEUED, 2),
            tuple(secondKey, CommandDistributionIntent.ENQUEUED, 3));

    // then first distribution is sent out immediately, second distribution isn't
    fakeProcessingResultBuilder.flushPostCommitTasks();
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(firstKey), any());
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(firstKey), any());
    verifyNoMoreInteractions(mockInterpartitionCommandSender);
  }

  @Nested
  class OnAcknowledge {
    CommandDistributionBehavior behavior;

    @BeforeEach
    void setUp() {
      behavior =
          new CommandDistributionBehavior(
              mockDistributionState,
              writers,
              1,
              RoutingInfo.forStaticPartitions(2),
              mockInterpartitionCommandSender,
              mockDistributionMetrics);
    }

    @Test
    public void shouldAcknowledgeCommandAndFinishDistribution() {
      final CommandDistributionRecord record = new CommandDistributionRecord().setPartitionId(2);

      when(mockDistributionState.hasPendingDistribution(123L)).thenReturn(false);

      behavior.onAcknowledgeDistribution(123L, record);

      Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
          .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
          .containsExactly(
              tuple(123L, CommandDistributionIntent.ACKNOWLEDGED, 2),
              tuple(123L, CommandDistributionIntent.FINISH, 1));
    }

    @Test
    public void shouldAcknowledgeCommandAndNotFinishDistribution() {
      final CommandDistributionRecord record = new CommandDistributionRecord().setPartitionId(2);

      when(mockDistributionState.hasPendingDistribution(123L)).thenReturn(true);

      behavior.onAcknowledgeDistribution(123L, record);

      Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
          .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
          .containsExactly(tuple(123L, CommandDistributionIntent.ACKNOWLEDGED, 2));
    }

    @Test
    public void shouldAcknowledgeCommandAndContinueQueue() {
      final CommandDistributionRecord record = new CommandDistributionRecord().setPartitionId(2);
      final CommandDistributionRecord otherRecord =
          new CommandDistributionRecord().setPartitionId(2);

      when(mockDistributionState.getQueueIdForDistribution(123L)).thenReturn(Optional.of("queue"));
      when(mockDistributionState.getNextQueuedDistributionKey("queue", 2))
          .thenReturn(Optional.of(124L));
      when(mockDistributionState.getCommandDistributionRecord(124L, 2)).thenReturn(otherRecord);
      when(mockDistributionState.hasPendingDistribution(123L)).thenReturn(false);

      behavior.onAcknowledgeDistribution(123L, record);

      Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
          .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
          .containsExactly(
              tuple(123L, CommandDistributionIntent.ACKNOWLEDGED, 2),
              tuple(124L, CommandDistributionIntent.DISTRIBUTING, 2),
              tuple(123L, CommandDistributionIntent.FINISH, 1));
    }
  }
}
