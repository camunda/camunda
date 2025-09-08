/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
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
    commandRedistributor = getCommandRedistributor(commandDistributionPaused);

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
        .partitions()
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
        .sendCommand(1, ValueType.USER, UserIntent.CREATE, distributionKey, recordValue);
  }

  private CommandRedistributor getCommandRedistributor(final boolean commandDistributionPaused) {
    final var config =
        new EngineConfiguration()
            .setCommandDistributionPaused(commandDistributionPaused)
            .setCommandRedistributionInterval(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL)
            .setCommandRedistributionMaxBackoff(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION);

    return new CommandRedistributor(
        processingState.getDistributionState(), mockCommandSender, config);
  }

  @Nested
  class PausedCommandRedistribution {

    @BeforeEach
    void setUp() {
      // Simulate command distribution paused
      final var commandDistributionPaused = true;
      commandRedistributor = getCommandRedistributor(commandDistributionPaused);
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
}
