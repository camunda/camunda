/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.DistributionState.PendingDistributionVisitor;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class CommandRedistributionSchedulerTest {

  private static final int RECEIVER_PARTITION = 2;
  private static final long DISTRIBUTION_KEY = 123L;

  @Test
  void shouldNotRetryOnFirstVisit() {
    // given — first cycle is 0; bitCount(0) != 1 -> no retry
    final var distributionBehavior = mock(CommandDistributionBehavior.class);
    final var record = newRecord();
    stubBehaviorToVisit(distributionBehavior, record);
    final var scheduler = newScheduler(distributionBehavior, /* scaling */ false);

    // when
    final Result result = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(result.decision()).isEqualTo(Decision.Idle.INSTANCE);
    verify(distributionBehavior, never()).onScheduledRetry(eq(DISTRIBUTION_KEY), any());
  }

  @Test
  void shouldRetryOnSecondVisit() {
    // given — second visit -> cycle=1; bitCount(1)==1 -> retry
    final var distributionBehavior = mock(CommandDistributionBehavior.class);
    final var record = newRecord();
    stubBehaviorToVisit(distributionBehavior, record);
    final var scheduler = newScheduler(distributionBehavior, /* scaling */ false);
    scheduler.run(FakeTaskContext.create().withClockMillis(1_000L)); // priming visit

    // when
    scheduler.run(FakeTaskContext.create().withClockMillis(2_000L));

    // then
    verify(distributionBehavior, times(1)).onScheduledRetry(eq(DISTRIBUTION_KEY), eq(record));
  }

  @Test
  void shouldSkipDistributionsForScalingPartition() {
    // given
    final var distributionBehavior = mock(CommandDistributionBehavior.class);
    final var record = newRecord();
    stubBehaviorToVisit(distributionBehavior, record);
    final var scheduler = newScheduler(distributionBehavior, /* scaling */ true);

    // when — second visit would normally retry, but partition is scaling
    scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));
    scheduler.run(FakeTaskContext.create().withClockMillis(2_000L));

    // then
    verify(distributionBehavior, never()).onScheduledRetry(anyLong(), any());
  }

  private static CommandDistributionRecord newRecord() {
    return new CommandDistributionRecord()
        .setPartitionId(RECEIVER_PARTITION)
        .setValueType(ValueType.DEPLOYMENT)
        .setIntent(DeploymentIntent.CREATE);
  }

  private static void stubBehaviorToVisit(
      final CommandDistributionBehavior behavior, final CommandDistributionRecord record) {
    doAnswer(
            inv -> {
              final PendingDistributionVisitor visitor = inv.getArgument(0);
              visitor.visit(DISTRIBUTION_KEY, record);
              return null;
            })
        .when(behavior)
        .foreachRetriableDistribution(any());
  }

  private static CommandRedistributionScheduler newScheduler(
      final CommandDistributionBehavior distributionBehavior, final boolean scaling) {
    final var routing = mock(RoutingInfo.class);
    when(routing.isPartitionScaling(RECEIVER_PARTITION)).thenReturn(scaling);
    final var config =
        new EngineConfiguration()
            .setCommandRedistributionInterval(Duration.ofSeconds(10))
            .setCommandRedistributionMaxBackoff(Duration.ofMinutes(5));
    return new CommandRedistributionScheduler(distributionBehavior, routing, config);
  }
}
