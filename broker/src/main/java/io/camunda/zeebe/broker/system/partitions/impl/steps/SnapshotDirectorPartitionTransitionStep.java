/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import akka.actor.typed.ActorSystem;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaCompatActor;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;

public final class SnapshotDirectorPartitionTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (context.getSnapshotDirector() != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      final var director = (AsyncSnapshotDirector) context.getSnapshotDirector();
      context.getComponentHealthMonitor().removeComponent(director.getName());
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

      final Duration snapshotPeriod = context.getBrokerCfg().getData().getSnapshotPeriod();
      final AsyncSnapshotDirector director;
      if (targetRole == Role.LEADER) {
        director =
            AsyncSnapshotDirector.ofProcessingMode(
                context.getNodeId(),
                context.getPartitionId(),
                context.getStreamProcessor(),
                context.getStateController(),
                snapshotPeriod);
      } else {
        director =
            AsyncSnapshotDirector.ofReplayMode(
                context.getNodeId(),
                context.getPartitionId(),
                context.getStreamProcessor(),
                context.getStateController(),
                snapshotPeriod);
      }

      final var future = context.getActorSchedulingService().submitActor(director);
      future.onComplete(
          (ok, error) -> {
            if (error == null) {
              context.setSnapshotDirector(director);
              context.getComponentHealthMonitor().registerComponent(director.getName(), director);
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

  public ActorFuture<Void> prepareTransitionAkka(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (context.getSnapshotDirector() != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      final var compat = new AkkaCompatActor();
      context.getActorSchedulingService().submitActor(compat);
      final var mode = switch (targetRole) {
        case LEADER -> StreamProcessorMode.PROCESSING;
        default -> StreamProcessorMode.REPLAY;
      };
      final var snapshotDirector = new AkkaSnapshotDirector(compat, context.getStreamProcessor(), mode, context.getStateController());

      final var actorSystem = ActorSystem.create(snapshotDirector.create(), "SnapshotDirector");
      context.setSnapshotDirector(snapshotDirector);
      if (targetRole == Role.LEADER) {
        context.getRaftPartition().getServer().addCommittedEntryListener(snapshotDirector);
      }
    }
    return CompletableActorFuture.completed(null);
  }

  public ActorFuture<Void> transitionToAkka(
      final PartitionTransitionContext context, final long term, final Role targetRole) {

  }
  private boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }
}
