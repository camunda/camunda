/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.state.immutable.DistributionState.PendingDistributionVisitor;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import org.junit.Before;
import org.junit.Test;

public class CommandRedistributorTest {

  private CommandDistributionBehavior behavior;
  private RoutingInfo routingInfo;

  @Before
  public void setUp() {
    behavior = mock(CommandDistributionBehavior.class);
    routingInfo = mock(RoutingInfo.class);
  }

  @Test
  public void shouldNotRetryOnFirstCycle() {
    final var redistributor = new CommandRedistributor(behavior, routingInfo);
    final var distributionRecord = new CommandDistributionRecord().setPartitionId(2);

    doAnswer(
            invocation -> {
              return invocation
                  .<PendingDistributionVisitor>getArgument(0)
                  .visit(123L, distributionRecord);
            })
        .when(behavior)
        .foreachRetriableDistribution(any(PendingDistributionVisitor.class));

    redistributor.runRetryCycle();

    verify(behavior, never()).onScheduledRetry(123L, distributionRecord);
  }

  @Test
  public void shouldRetryOnSecondCycle() {
    final var redistributor = new CommandRedistributor(behavior, routingInfo);
    final var distributionRecord = new CommandDistributionRecord().setPartitionId(2);

    doAnswer(
            invocation -> {
              return invocation
                  .<PendingDistributionVisitor>getArgument(0)
                  .visit(123L, distributionRecord);
            })
        .when(behavior)
        .foreachRetriableDistribution(any(PendingDistributionVisitor.class));

    redistributor.runRetryCycle();
    redistributor.runRetryCycle();

    verify(behavior).onScheduledRetry(123L, distributionRecord);
  }

  private void addDistribution(final CommandDistributionRecord... records) {}
}
