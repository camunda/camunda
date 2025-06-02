/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.distribution.CommandRedistributor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.state.routing.RoutingInfo.StaticRoutingInfo;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, ProcessingStateExtension.class})
public class CommandRedistributorScalingTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableRoutingState routingState;
  @Mock private DistributionMetrics mockDistributionMetrics;
  @Mock private InterPartitionCommandSender mockCommandSender;

  private FakeProcessingResultBuilder<CommandDistributionRecord> fakeProcessingResultBuilder;
  private Writers writers;
  private CommandRedistributor commandRedistributor;
  private long distributionKey;
  private CommandDistributionBehavior behavior;
  private UserRecord recordValue;

  @BeforeEach
  public void setUp() {
    final var distributionState = processingState.getDistributionState();
    routingState = processingState.getRoutingState();
    routingState.initializeRoutingInfo(2);
    routingState.setDesiredPartitions(Set.of(1, 2, 3));

    fakeProcessingResultBuilder = new FakeProcessingResultBuilder<>();
    writers = new Writers(() -> fakeProcessingResultBuilder, mock(EventAppliers.class));

    final RoutingInfo routingInfo =
        RoutingInfo.dynamic(routingState, new StaticRoutingInfo(Set.of(1, 2), 2));

    behavior =
        new CommandDistributionBehavior(
            processingState.getDistributionState(),
            writers,
            1,
            routingInfo,
            mockCommandSender,
            mockDistributionMetrics);

    commandRedistributor = new CommandRedistributor(behavior, routingInfo);

    recordValue =
        new UserRecord()
            .setUserKey(1L)
            .setEmail("test@test.com")
            .setName("foo")
            .setPassword("bar")
            .setUsername("foobar");

    final var distributionRecord =
        new CommandDistributionRecord()
            .setPartitionId(1)
            .setValueType(ValueType.USER)
            .setIntent(UserIntent.CREATE)
            .setCommandValue(recordValue);

    distributionKey = Protocol.encodePartitionId(1, 100);

    distributionState.addCommandDistribution(distributionKey, distributionRecord);
    // Add pending command distributions for partitions 1, 2, and 3
    routingState
        .desiredPartitions()
        .forEach(
            partition -> distributionState.addRetriableDistribution(distributionKey, partition));
  }

  @Test
  void shouldNotRedistributeToScalingPartitions() {
    // given
    // when
    // run cycle twice, since first one always does not try distributing the records
    commandRedistributor.runRetryCycle();
    commandRedistributor.runRetryCycle();

    // then
    verify(mockCommandSender, times(1))
        .sendCommand(1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
    verify(mockCommandSender, times(1))
        .sendCommand(2, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
    verify(mockCommandSender, never())
        .sendCommand(3, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
  }

  @Test
  void shouldRedistributeToScaledPartition() {
    // given
    // when
    // run cycle twice, since first one always does not try distributing the records
    commandRedistributor.runRetryCycle();
    commandRedistributor.runRetryCycle();

    // then
    verify(mockCommandSender, times(1))
        .sendCommand(1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
    verify(mockCommandSender, times(1))
        .sendCommand(2, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
    verify(mockCommandSender, never())
        .sendCommand(3, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);

    // then
    // Partition 3 is now scaled up, so it should be redistributed to
    routingState.arriveAtDesiredState();

    // run cycle twice, since first one always does not try distributing the records
    commandRedistributor.runRetryCycle();
    commandRedistributor.runRetryCycle();
    verify(mockCommandSender, times(1))
        .sendCommand(3, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
  }
}
