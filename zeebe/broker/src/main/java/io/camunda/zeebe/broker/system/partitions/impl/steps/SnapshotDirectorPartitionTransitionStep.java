/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.logstreams.state.DbPositionSupplier;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
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

      final var processingMode = StreamProcessorMode.fromRole(targetRole.isLeader());
      final var zeebeDb = context.getZeebeDb();
      final var continuousBackup = context.getBrokerCfg().getData().getBackup().isContinuous();
      final var director =
          AsyncSnapshotDirector.of(
              context.getPartitionId(),
              context.getStreamProcessor(),
              context.getStateController(),
              processingMode,
              snapshotPeriod,
              flushLog,
              new DbPositionSupplier(zeebeDb, continuousBackup));

      final var future =
          context.getActorSchedulingService().submitActor(director, SchedulingHints.cpuBound());
      future.onComplete(
          (ok, error) -> {
            if (error == null) {
              context.setSnapshotDirector(director);
              context.getComponentHealthMonitor().registerComponent(director);
              if (targetRole == Role.LEADER) {
                server.addCommittedEntryListener(director);

                // Raft server will only notify if there is a new entry commited. If the node has
                // just restarted or transitioned to leader, but there are no new processing records
                // written or committed, the listener is not triggered. As a result the
                // commitPosition in the snapshotDirector remain 0, thus preventing snapshots even
                // if the state has changed after replaying previously committed events. Hence, set
                // the commit position from the last record in the log stream.
                try (final LogStreamReader logStreamReader =
                    context.getLogStream().newLogStreamReader()) {
                  final var commitPosition = logStreamReader.seekToEnd();
                  director.onCommit(commitPosition);
                }
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
