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

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
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
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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

  private FakeProcessingResultBuilder<CommandDistributionRecord> fakeProcessingResultBuilder;
  private InterPartitionCommandSender mockInterpartitionCommandSender;
  private Writers writers;

  private long key;
  private ValueType valueType;
  private DeploymentIntent intent;
  private MockTypedRecord<DeploymentRecord> command;

  @BeforeEach
  void setUp() {
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
        new CommandDistributionBehavior(writers, 1, 1, mockInterpartitionCommandSender);

    // when distributing to all partitions
    behavior.distributeCommand(key, command);

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
        new CommandDistributionBehavior(writers, 1, 3, mockInterpartitionCommandSender);

    // when distributing to all partitions
    behavior.distributeCommand(key, command);

    // then command distribution is started on partition 1 and distributing to all other partitions
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .extracting(
            Record::getKey,
            Record::getIntent,
            r -> r.getValue().getPartitionId(),
            r -> r.getValue().getIntent())
        .containsExactly(
            tuple(key, CommandDistributionIntent.STARTED, 1, intent),
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
        new CommandDistributionBehavior(writers, 2, 4, mockInterpartitionCommandSender);

    // when distributing to partitions 1 and 3
    behavior.distributeCommand(key, command, List.of(1, 3));

    // then command distribution is started on partition 2 and distributing to all other partitions
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .extracting(
            Record::getKey,
            Record::getIntent,
            r -> r.getValue().getPartitionId(),
            r -> r.getValue().getIntent())
        .containsExactly(
            tuple(key, CommandDistributionIntent.STARTED, 2, intent),
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
}
