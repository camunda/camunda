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
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.migration.DbMigratorImpl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import java.time.InstantSource;

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

    // migration
    final var transientMessageSubscriptionState = new TransientPendingSubscriptionState();
    final var transientProcessMessageSubscriptionState = new TransientPendingSubscriptionState();
    final var zeebeDb = context.getZeebeDb();
    final var zeebeDbContext = zeebeDb.createContext();

    final var processingState =
        new ProcessingDbState(
            context.getPartitionId(),
            zeebeDb,
            zeebeDbContext,
            KeyGenerator.immutable(context.getPartitionId()),
            transientMessageSubscriptionState,
            transientProcessMessageSubscriptionState,
            context.getBrokerCfg().getExperimental().getEngine().createEngineConfiguration(),
            InstantSource.system(),
            ExpressionLanguageMetrics.noop());

    final var dbMigrator =
        new DbMigratorImpl(
            context.getBrokerCfg().getExperimental().isVersionCheckRestrictionEnabled(),
            new ClusterContextImpl(context.getPartitionCount()),
            processingState,
            context.getBrokerVersion());
    try {
      final var migrationsPerformed = dbMigrator.runMigrations();
      zeebeDbContext.getCurrentTransaction().commit();
      if (migrationsPerformed.versionChanged()) {
        context.markMigrationsDone();
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
