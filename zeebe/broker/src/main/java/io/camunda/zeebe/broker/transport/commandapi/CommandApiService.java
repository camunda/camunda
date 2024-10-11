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
import io.camunda.zeebe.stream.api.CommandResponseWriter;

public interface CommandApiService extends PartitionTransitionStep {

  CommandResponseWriter newCommandResponseWriter();

  void onRecovered(final int partitionId);

  void onPaused(final int partitionId);

  void onResumed(final int partitionId);

  final class TransitionStep implements PartitionTransitionStep {
    @Override
    public void onNewRaftRole(final PartitionTransitionContext context, final Role newRole) {
      PartitionTransitionStep.super.onNewRaftRole(context, newRole);
      context.getCommandApiService().onNewRaftRole(context, newRole);
    }

    @Override
    public ActorFuture<Void> prepareTransition(
        final PartitionTransitionContext context, final long term, final Role targetRole) {
      // nothing is done here, everything should be done in onNewRaftRole
      return context.getCommandApiService().prepareTransition(context, term, targetRole);
    }

    @Override
    public ActorFuture<Void> transitionTo(
        final PartitionTransitionContext context, final long term, final Role targetRole) {
      return context.getCommandApiService().transitionTo(context, term, targetRole);
    }

    @Override
    public String getName() {
      return "CommandApiService";
    }
  }
}
