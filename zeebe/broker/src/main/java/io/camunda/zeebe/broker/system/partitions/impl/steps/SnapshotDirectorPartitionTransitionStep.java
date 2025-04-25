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
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public final class SnapshotDirectorPartitionTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final AsyncSnapshotDirector snapshotDirector = context.getSnapshotDirector();
    if (snapshotDirector != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      final var director = context.getSnapshotDirector();
      context.getComponentHealthMonitor().removeComponent(director);
      context.getRaftPartition().getServer().removeCommittedEntryListener(director);
      final ActorFuture<Void> future = director.closeAsync();
      future.onComplete(
          (ok, error) -> {
            if (error == null) {
              context.setSnapshotDirector(null);
            }
          });
      return future;
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if ((context.getSnapshotDirector() == null && targetRole != Role.INACTIVE)
        || shouldInstallOnTransition(targetRole, context.getCurrentRole())) {
      final var server = context.getRaftPartition().getServer();
      final Callable<CompletableFuture<Void>> flushLog = server::flushLog;

      final Duration snapshotPeriod = context.getBrokerCfg().getData().getSnapshotPeriod();
      final AsyncSnapshotDirector director;
      if (targetRole == Role.LEADER) {
        director =
            AsyncSnapshotDirector.ofProcessingMode(
                context.getPartitionId(),
                context.getStreamProcessor(),
                context.getStateController(),
                snapshotPeriod,
                flushLog);
      } else {
        director =
            AsyncSnapshotDirector.ofReplayMode(
                context.getPartitionId(),
                context.getStreamProcessor(),
                context.getStateController(),
                snapshotPeriod,
                flushLog);
      }

      final var future =
          context.getActorSchedulingService().submitActor(director, SchedulingHints.cpuBound());
      future.onComplete(
          (ok, error) -> {
            if (error == null) {
              context.setSnapshotDirector(director);
              context.getComponentHealthMonitor().registerComponent(director);
              if (targetRole == Role.LEADER) {
                server.addCommittedEntryListener(director);
              }
            }
          });
      return future;

    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public String getName() {
    return "SnapshotDirector";
  }

  private boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }
}
