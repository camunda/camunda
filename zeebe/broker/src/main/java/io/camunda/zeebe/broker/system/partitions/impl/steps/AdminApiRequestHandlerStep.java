/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public final class AdminApiRequestHandlerStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (!targetRole.active()) {
      final var adminApiService = context.getAdminApiService();
      if (adminApiService != null) {
        context.setAdminApiRequestHandler(null);
        return adminApiService.closeAsync();
      }
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (!targetRole.active() || context.getAdminApiService() != null) {
      return CompletableActorFuture.completed(null);
    }

    final var concurrencyControl = context.getConcurrencyControl();
    final var schedulingService = context.getActorSchedulingService();
    final var transport = context.getGatewayBrokerTransport();
    final var handler =
        new AdminApiRequestHandler(transport, context.getAdminAccess(), context.getRaftPartition());
    final var submitFuture = schedulingService.submitActor(handler);
    concurrencyControl.runOnCompletion(
        submitFuture,
        (ok, error) -> {
          if (error == null) {
            context.setAdminApiRequestHandler(handler);
          }
        });
    return submitFuture;
  }

  @Override
  public String getName() {
    return "Admin API";
  }
}
