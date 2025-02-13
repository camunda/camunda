/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

public final class MetricsStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var transitionMeterRegistry =
        (CompositeMeterRegistry) context.getPartitionTransitionMeterRegistry();
    if (transitionMeterRegistry != null) {
      MicrometerUtil.discard(transitionMeterRegistry);
      context.setPartitionTransitionMeterRegistry(null);
    }
    return context.getConcurrencyControl().createCompletedFuture();
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var startupMeterRegistry = context.getPartitionStartupMeterRegistry();
    final var transitionRegistry =
        MicrometerUtil.wrap(startupMeterRegistry, PartitionKeyNames.tags(context.getPartitionId()));

    context.setPartitionTransitionMeterRegistry(transitionRegistry);
    return context.getConcurrencyControl().createCompletedFuture();
  }

  @Override
  public String getName() {
    return "Metrics";
  }
}
