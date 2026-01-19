/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public class CommandApiServiceTransitionStep implements PartitionTransitionStep {
  @Override
  public void onNewRaftRole(final PartitionTransitionContext context, final Role newRole) {}

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    return switch (targetRole) {
      case LEADER -> context.getConcurrencyControl().createCompletedFuture();
      default -> context.getCommandApiService().unregisterHandlers(context.getFullPartitionId());
    };
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    return switch (targetRole) {
      case LEADER ->
          context
              .getCommandApiService()
              .registerHandlers(
                  context.getFullPartitionId(), context.getLogStream(), context.getQueryService());
      default -> context.getConcurrencyControl().createCompletedFuture();
    };
  }

  @Override
  public String getName() {
    return "CommandApiService";
  }
}
