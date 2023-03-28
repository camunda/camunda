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
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.query.StateQueryService;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.agrona.CloseHelper;

public final class QueryServicePartitionTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var currentRole = context.getCurrentRole();
    final QueryService queryService = context.getQueryService();
    if (queryService != null && (currentRole == Role.LEADER || targetRole == Role.INACTIVE)) {
      try {
        CloseHelper.close(queryService);
        context.setQueryService(null);
        return CompletableActorFuture.completed(null);
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }
    }

    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var currentRole = context.getCurrentRole();

    if (targetRole != Role.INACTIVE
        && (currentRole == Role.LEADER || context.getQueryService() == null)) {
      try {
        final var service = new StateQueryService(context.getZeebeDb(), ActorClock.current());
        context.setQueryService(service);
        return CompletableActorFuture.completed(null);
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }
    }

    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "QueryService";
  }
}
