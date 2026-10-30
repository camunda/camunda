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
import io.camunda.zeebe.engine.api.EngineFactory;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;

public class MigrationTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (targetRole == Role.INACTIVE) {
      return CompletableActorFuture.completed(null);
    }

    try {
      final var config = context.getBrokerCfg().getExperimental();
      final var versionChanged =
          EngineFactory.runMigrations(
              context.getPartitionId(),
              context.getZeebeDb(),
              config.getEngine().createEngineConfiguration(),
              config.isVersionCheckRestrictionEnabled(),
              new ClusterContextImpl(context.getPartitionCount()),
              context.getBrokerVersion());
      if (versionChanged) {
        context.markMigrationsDone();
        context.resetMigrationSnapshotTaken();
      } else if (!context.areMigrationsPerformed()) {
        context.markMigrationSnapshotTaken();
      }
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "Migration";
  }
}
