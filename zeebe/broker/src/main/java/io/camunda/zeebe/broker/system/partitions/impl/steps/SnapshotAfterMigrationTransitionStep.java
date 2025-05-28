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
import io.camunda.zeebe.broker.system.partitions.impl.MigrationSnapshotDirector;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.VisibleForTesting;

public class SnapshotAfterMigrationTransitionStep implements PartitionTransitionStep {

  private MigrationSnapshotDirector migrationSnapshotDirector;

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (targetRole == Role.INACTIVE & migrationSnapshotDirector != null) {
      migrationSnapshotDirector.close();
      migrationSnapshotDirector = null;
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (targetRole != Role.INACTIVE) {
      if (migrationSnapshotDirector == null && context.areMigrationsPerformed()) {
        migrationSnapshotDirector =
            new MigrationSnapshotDirector(
                context.getSnapshotDirector(),
                context.getConcurrencyControl(),
                context.getComponentHealthMonitor());
      }
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public String getName() {
    return "SnapshotAfterMigrationTransitionStep";
  }

  @VisibleForTesting
  boolean isSnapshotTaken() {
    return migrationSnapshotDirector.isSnapshotTaken();
  }
}
