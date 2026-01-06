/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.AtomicKeyGenerator;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class CommandDistributionScalingTest {
  private static final int PARTITION_ID = 1;
  /* Injected from {@link ProcessingStateExtension} */
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState state;
  private TransactionContext transactionContext;
  private DistributionState distributionState;
  private FakeProcessingResultBuilder<CommandDistributionRecord> fakeProcessingResultBuilder;
  private InterPartitionCommandSender mockInterpartitionCommandSender;
  private Writers writers;

  private KeyGenerator keyGenerator;
  private ValueType valueType;
  private DeploymentIntent intent;
  private MockTypedRecord<DeploymentRecord> command;
  private CommandDistributionBehavior behavior;

  @BeforeEach
  public void setup() {
    distributionState = new DbDistributionState(zeebeDb, transactionContext);
    fakeProcessingResultBuilder = new FakeProcessingResultBuilder<>();
    mockInterpartitionCommandSender = mock(InterPartitionCommandSender.class);
    final var eventAppliers = new EventAppliers().registerEventAppliers(state);
    keyGenerator = new AtomicKeyGenerator(1);
    writers = new Writers(() -> fakeProcessingResultBuilder, eventAppliers);
    writers.setKeyValidator(keyGenerator);

    valueType = ValueType.DEPLOYMENT;
    intent = DeploymentIntent.CREATE;
    command =
        new MockTypedRecord<>(
            keyGenerator.getCurrentKey(),
            new RecordMetadata().valueType(valueType).intent(intent),
            new DeploymentRecord());

    // given a scale operation ongoing to transition to 3 partitions
    state.getRoutingState().initializeRoutingInfo(2);
    state.getRoutingState().setDesiredPartitions(Set.of(1, 2, 3), 1L);
    behavior =
        new CommandDistributionBehavior(
            distributionState,
            writers,
            1,
            RoutingInfo.dynamic(state.getRoutingState(), RoutingInfo.forStaticPartitions(2)),
            mockInterpartitionCommandSender,
            mock(DistributionMetrics.class));
  }

  @Test
  public void shouldWaitInQueueDuringScaling() {

    // when distributing first command in queue to all partitions
    final var key = keyGenerator.nextKey();
    behavior.withKey(key).inQueue("test-queue").distribute(command);

    // then command distribution is started on partition 1, distribution is enqueued and triggered
    // immediately for partition 2 and partition 3 gets enqueued
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
        .startsWith(tuple(key, CommandDistributionIntent.STARTED, 1))
        .containsSequence(
            tuple(key, CommandDistributionIntent.ENQUEUED, 2),
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 2))
        .contains(tuple(key, CommandDistributionIntent.ENQUEUED, 3));

    fakeProcessingResultBuilder.flushPostCommitTasks();
    // then command is sent immediately to partition 2
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(key), any(), any());
    verify(mockInterpartitionCommandSender, never())
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(key), any(), any());
    verifyNoMoreInteractions(mockInterpartitionCommandSender);
    assertThat(distributionState.hasPendingDistribution(key, 3)).isTrue();
    assertThat(distributionState.hasRetriableDistribution(key, 3)).isTrue();
  }

  @Test
  public void shouldQueueUpDistributions() {
    // given

    // when a distribution command is sent for two records
    final var key = keyGenerator.getCurrentKey();
    final var otherKey = keyGenerator.nextKey();
    behavior.withKey(key).inQueue("test-queue").distribute(command);
    behavior.withKey(otherKey).inQueue("test-queue").distribute(command);

    // then command distribution is started on partition 1
    // first record is starting to be distributed
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .filteredOn(f -> f.getKey() == key)
        .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
        .startsWith(tuple(key, CommandDistributionIntent.STARTED, 1))
        .containsSequence(
            tuple(key, CommandDistributionIntent.ENQUEUED, 2),
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 2))
        .containsSequence(tuple(key, CommandDistributionIntent.ENQUEUED, 3));

    // second record is enqueued for partitions 2 and 3
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .filteredOn(f -> f.getKey() == otherKey)
        .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
        .startsWith(tuple(otherKey, CommandDistributionIntent.STARTED, 1))
        .contains(
            tuple(otherKey, CommandDistributionIntent.ENQUEUED, 2),
            tuple(otherKey, CommandDistributionIntent.ENQUEUED, 3));

    fakeProcessingResultBuilder.flushPostCommitTasks();
    // then command is sent immediately to partition 2 for the first record
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(key), any(), any());
    verify(mockInterpartitionCommandSender, never())
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(key), any(), any());
    // the second command is enqueued for partitions 2 and 3
    verify(mockInterpartitionCommandSender, never())
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(otherKey), any(), any());
    verify(mockInterpartitionCommandSender, never())
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(otherKey), any(), any());
    verifyNoMoreInteractions(mockInterpartitionCommandSender);

    assertThat(distributionState.hasPendingDistribution(key, 2)).isTrue();
    assertThat(distributionState.hasPendingDistribution(key, 3)).isTrue();
    assertThat(distributionState.hasRetriableDistribution(key, 3)).isTrue();
    assertThat(distributionState.hasPendingDistribution(otherKey, 2)).isTrue();
    assertThat(distributionState.hasPendingDistribution(otherKey, 3)).isTrue();
  }

  @Test
  public void shouldNotDistributeToDesiredPartitionsIfCommandSpecifiesPartition() {
    // given

    // when a distribution command is sent for two records
    final var key = keyGenerator.nextKey();
    behavior.withKey(key).inQueue("test-queue").distribute(command);
    final var otherKey = keyGenerator.nextKey();
    behavior.withKey(otherKey).inQueue("test-queue").forPartition(2).distribute(command);

    // then command distribution is started on partition 1
    // first record is starting to be distributed
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .filteredOn(f -> f.getKey() == key)
        .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
        .startsWith(tuple(key, CommandDistributionIntent.STARTED, 1))
        .containsSequence(
            tuple(key, CommandDistributionIntent.ENQUEUED, 2),
            tuple(key, CommandDistributionIntent.DISTRIBUTING, 2))
        .contains(tuple(key, CommandDistributionIntent.ENQUEUED, 3));

    // second record is enqueued for partitions 2 and 3
    Assertions.assertThat(fakeProcessingResultBuilder.getFollowupRecords())
        .filteredOn(f -> f.getKey() == otherKey)
        .extracting(Record::getKey, Record::getIntent, r -> r.getValue().getPartitionId())
        .startsWith(tuple(otherKey, CommandDistributionIntent.STARTED, 1))
        .contains(tuple(otherKey, CommandDistributionIntent.ENQUEUED, 2));

    // then command is sent immediately to partition 2 for the first record
    fakeProcessingResultBuilder.flushPostCommitTasks();
    verify(mockInterpartitionCommandSender)
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(key), any(), any());
    verify(mockInterpartitionCommandSender, never())
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(key), any(), any());

    verify(mockInterpartitionCommandSender, never())
        .sendCommand(eq(2), eq(valueType), eq(intent), eq(otherKey), any(), any());
    verify(mockInterpartitionCommandSender, never())
        .sendCommand(eq(3), eq(valueType), eq(intent), eq(otherKey), any(), any());
    verifyNoMoreInteractions(mockInterpartitionCommandSender);

    assertThat(distributionState.hasPendingDistribution(key, 3)).isTrue();
    assertThat(distributionState.hasPendingDistribution(otherKey, 2)).isTrue();
    assertThat(distributionState.hasPendingDistribution(otherKey, 3)).isFalse();
  }
}
