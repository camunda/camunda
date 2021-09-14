/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionStartupAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.engine.state.query.StateQueryService;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.CloseHelper;

public final class QueryServiceStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionStartupAndTransitionContextImpl context) {
    try {
      final var service = new StateQueryService(context.getZeebeDb());
      context.setQueryService(service);
      return CompletableActorFuture.completed(null);
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  @Override
  public ActorFuture<Void> close(final PartitionStartupAndTransitionContextImpl context) {
    try {
      CloseHelper.close(context.getQueryService());
      context.setQueryService(null);
      return CompletableActorFuture.completed(null);
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  @Override
  public String getName() {
    return "queryService";
  }
}
