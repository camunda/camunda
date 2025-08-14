/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.state.routing.RoutingInfo.StaticRoutingInfo;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, ProcessingStateExtension.class})
public class CommandRedistributorConfigurationTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableRoutingState routingState;
  @Mock private DistributionMetrics mockDistributionMetrics;
  @Mock private InterPartitionCommandSender mockCommandSender;

  private MutableDistributionState distributionState;

  @BeforeEach
  public void setUp() {
    routingState = processingState.getRoutingState();
    distributionState = processingState.getDistributionState();

    routingState.setDesiredPartitions(Set.of(1, 2), 10L);

    final long distributionKey = 1L;
    final var recordValue = new UserRecord().setUserKey(1L);
    final var record = new CommandDistributionRecord();
    record.setPartitionId(1).setValueType(ValueType.USER).setIntent(UserIntent.CREATE);

    // Add a retriable distribution
    distributionState.addRetriableDistribution(distributionKey, 1);
  }

  @Test
  void shouldUseConfigurableRetryInterval() {
    // given - use a very short retry interval for testing
    final var customInterval = Duration.ofMillis(100);
    final var customMaxBackoff = Duration.ofMinutes(1);
    final var redistributor = createCommandRedistributor(false, customInterval, customMaxBackoff);

    // when - run multiple cycles to see retry behavior
    redistributor.runRetryCycle(); // cycle 0 - no retry (starts at 0)
    redistributor.runRetryCycle(); // cycle 1 - should retry (bitCount(1) == 1)

    // then - verify retry occurred
    verify(mockCommandSender, times(1))
        .sendCommand(1, ValueType.USER, UserIntent.CREATE, 1L, new UserRecord().setUserKey(1L));
  }

  @Test
  void shouldUseConfigurableMaxBackoff() {
    // given - use custom backoff values
    final var customInterval = Duration.ofSeconds(2);
    final var customMaxBackoff = Duration.ofSeconds(8); // Only 4 cycles until max backoff
    final var redistributor = createCommandRedistributor(false, customInterval, customMaxBackoff);

    // when - run enough cycles to reach max backoff
    for (int i = 0; i < 10; i++) {
      redistributor.runRetryCycle();
    }

    // then - verify retries occurred based on exponential backoff and then fixed intervals
    // Cycle 0: no retry
    // Cycle 1: retry (bitCount(1) == 1)
    // Cycle 2: retry (bitCount(2) == 1)
    // Cycle 3: no retry (bitCount(3) == 2)
    // Cycle 4: retry (4 >= maxRetryCycles=4 and 4 % 4 == 0)
    // Cycle 5-7: no retry (not multiple of 4)
    // Cycle 8: retry (8 % 4 == 0)
    // Cycle 9: no retry

    verify(mockCommandSender, times(4))
        .sendCommand(1, ValueType.USER, UserIntent.CREATE, 1L, new UserRecord().setUserKey(1L));
  }

  @Test
  void shouldRespectPausedDistributionFlag() {
    // given - distribution is paused
    final var redistributor = createCommandRedistributor(true, Duration.ofSeconds(1), Duration.ofMinutes(1));

    // when - run retry cycles
    redistributor.runRetryCycle();
    redistributor.runRetryCycle();

    // then - no retries should occur
    verify(mockCommandSender, never())
        .sendCommand(1, ValueType.USER, UserIntent.CREATE, 1L, new UserRecord().setUserKey(1L));
  }

  private CommandRedistributor createCommandRedistributor(
      final boolean commandDistributionPaused,
      final Duration retryInterval,
      final Duration maxBackoffDuration) {
    final var fakeProcessingResultBuilder = new FakeProcessingResultBuilder<>();
    final Writers writers =
        new Writers(() -> fakeProcessingResultBuilder, mock(EventAppliers.class));

    final RoutingInfo routingInfo =
        RoutingInfo.dynamic(routingState, new StaticRoutingInfo(Set.of(1, 2), 2));

    final CommandDistributionBehavior behavior =
        new CommandDistributionBehavior(
            distributionState,
            writers,
            1,
            routingInfo,
            mockCommandSender,
            mockDistributionMetrics);

    return new CommandRedistributor(
        behavior, routingInfo, commandDistributionPaused, retryInterval, maxBackoffDuration);
  }
}