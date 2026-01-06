/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.AtomicKeyGenerator;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.state.routing.RoutingInfo.StaticRoutingInfo;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, ProcessingStateExtension.class})
public class CommandRedistributorTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableRoutingState routingState;
  @Mock private DistributionMetrics mockDistributionMetrics;
  @Mock private InterPartitionCommandSender mockCommandSender;
  @Mock private ReadonlyStreamProcessorContext mockContext;

  private CommandRedistributor commandRedistributor;
  private long distributionKey;
  private UserRecord recordValue;
  private MutableDistributionState distributionState;

  @BeforeEach
  public void setUp() {
    distributionState = processingState.getDistributionState();
    routingState = processingState.getRoutingState();
    routingState.initializeRoutingInfo(2);

    final var commandDistributionPaused = false;
    commandRedistributor =
        getCommandRedistributor(
            commandDistributionPaused,
            EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL,
            EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION);

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
    // Add pending command distributions for partitions
    routingState
        .desiredPartitions()
        .forEach(
            partition -> distributionState.addRetriableDistribution(distributionKey, partition));
  }

  @Test
  public void shouldNotRetryOnFirstCycle() {
    // given
    // when
    commandRedistributor.runRetryCycle();

    // then
    verify(mockCommandSender, never())
        .sendCommand(1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
    verify(mockCommandSender, never())
        .sendCommand(2, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
  }

  @Test
  public void shouldRetryOnSecondCycle() {
    // given
    // when
    commandRedistributor.runRetryCycle();
    commandRedistributor.runRetryCycle();

    // then
    verify(mockCommandSender, times(1))
        .sendCommand(
            1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
  }

  private CommandRedistributor getCommandRedistributor(
      final boolean commandDistributionPaused,
      final Duration redistributionInterval,
      final Duration maxBackoffDuration) {
    final var fakeProcessingResultBuilder = new FakeProcessingResultBuilder<>();
    final var keyGenerator = new AtomicKeyGenerator(1);
    final Writers writers =
        new Writers(() -> fakeProcessingResultBuilder, mock(EventAppliers.class));
    writers.setKeyValidator(keyGenerator);

    final RoutingInfo routingInfo =
        RoutingInfo.dynamic(routingState, new StaticRoutingInfo(Set.of(1, 2), 2));

    final CommandDistributionBehavior behavior =
        new CommandDistributionBehavior(
            processingState.getDistributionState(),
            writers,
            1,
            routingInfo,
            mockCommandSender,
            mockDistributionMetrics);

    final var config =
        new EngineConfiguration()
            .setCommandDistributionPaused(commandDistributionPaused)
            .setCommandRedistributionInterval(redistributionInterval)
            .setCommandRedistributionMaxBackoff(maxBackoffDuration);

    return new CommandRedistributor(behavior, routingInfo, config);
  }

  @Nested
  class ScalingUpPartitions {

    @BeforeEach
    void setUp() {
      // Simulate scaling up partition 3
      routingState.setDesiredPartitions(Set.of(1, 2, 3), 111L);

      distributionState.addRetriableDistribution(distributionKey, 3);
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
          .sendCommand(
              1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
      verify(mockCommandSender, times(1))
          .sendCommand(
              2, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
      verify(mockCommandSender, never())
          .sendCommand(
              3, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
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
          .sendCommand(
              1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
      verify(mockCommandSender, times(1))
          .sendCommand(
              2, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
      verify(mockCommandSender, never())
          .sendCommand(
              3, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());

      // then
      // Partition 3 is now scaled up, so it should be redistributed to
      routingState.activatePartition(3);

      // run cycle twice, since first one always does not try distributing the records
      commandRedistributor.runRetryCycle();
      commandRedistributor.runRetryCycle();
      verify(mockCommandSender, times(1))
          .sendCommand(
              3, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
    }
  }

  @Nested
  class PausedCommandRedistribution {

    @BeforeEach
    void setUp() {
      // Simulate command distribution paused
      final var commandDistributionPaused = true;
      commandRedistributor =
          getCommandRedistributor(
              commandDistributionPaused,
              EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL,
              EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION);
    }

    @Test
    void shouldNotRetryOnPausedCommandDistribution() {
      // given
      // when
      commandRedistributor.onRecovered(mockContext);

      // then
      verify(mockContext, never()).getScheduleService();
    }
  }

  @Nested
  class ConfigurableRetryIntervals {

    @BeforeEach
    void setUp() {
      // Set up routing state for configuration tests
      final var customInterval = Duration.ofSeconds(2);
      final var customMaxBackoff = Duration.ofSeconds(8); // Only 4 cycles until max backoff
      commandRedistributor = getCommandRedistributor(false, customInterval, customMaxBackoff);
    }

    @Test
    void shouldUseConfigurableMaxBackoff() {
      // given
      // when - run enough cycles to reach max backoff
      for (int i = 0; i < 10; i++) {
        commandRedistributor.runRetryCycle();
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
          .sendCommand(
              1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
      verify(mockCommandSender, times(4))
          .sendCommand(
              2, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue, new AuthInfo());
    }
  }
}
