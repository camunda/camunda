/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import java.time.Duration;
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

  private SignalRecord recordValue;
  @Mock private InterPartitionCommandSender mockCommandSender;
  @Mock private ReadonlyStreamProcessorContext mockContext;

  private CommandRedistributor commandRedistributor;
  private long distributionKey;
  private MutableDistributionState distributionState;

  @BeforeEach
  public void setUp() {
    distributionState = processingState.getDistributionState();

    final var commandDistributionPaused = false;
    commandRedistributor =
        getCommandRedistributor(
            commandDistributionPaused,
            EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL,
            EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION);

    recordValue =
        new SignalRecord()
            .setSignalName("testSignal")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final var distributionRecord =
        new CommandDistributionRecord()
            .setPartitionId(1)
            .setValueType(ValueType.SIGNAL)
            .setIntent(SignalIntent.BROADCAST)
            .setCommandValue(recordValue);

    distributionKey = Protocol.encodePartitionId(1, 100);
    distributionState.addCommandDistribution(distributionKey, distributionRecord);
    for (int partition = 1; partition <= 2; partition++) {
      distributionState.addPendingDistribution(distributionKey, partition);
    }
  }

  @Test
  public void shouldNotRetryOnFirstCycle() {
    // given
    // when
    commandRedistributor.runRetryCycle();

    // then
    verify(mockCommandSender, never())
        .sendCommand(1, ValueType.SIGNAL, SignalIntent.BROADCAST, distributionKey, recordValue);
    verify(mockCommandSender, never())
        .sendCommand(2, ValueType.SIGNAL, SignalIntent.BROADCAST, distributionKey, recordValue);
  }

  @Test
  public void shouldRetryOnSecondCycle() {
    // given
    // when
    commandRedistributor.runRetryCycle();
    commandRedistributor.runRetryCycle();

    // then
    verify(mockCommandSender, times(1))
        .sendCommand(1, ValueType.SIGNAL, SignalIntent.BROADCAST, distributionKey, recordValue);
  }

  private CommandRedistributor getCommandRedistributor(
      final boolean commandDistributionPaused,
      final Duration redistributionInterval,
      final Duration maxBackoffDuration) {
    final var config =
        new EngineConfiguration()
            .setCommandDistributionPaused(commandDistributionPaused)
            .setCommandRedistributionInterval(redistributionInterval)
            .setCommandRedistributionMaxBackoff(maxBackoffDuration);

    return new CommandRedistributor(
        processingState.getDistributionState(), mockCommandSender, config);
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
          .sendCommand(1, ValueType.SIGNAL, SignalIntent.BROADCAST, distributionKey, recordValue);
      verify(mockCommandSender, times(4))
          .sendCommand(2, ValueType.SIGNAL, SignalIntent.BROADCAST, distributionKey, recordValue);
    }
  }
}
