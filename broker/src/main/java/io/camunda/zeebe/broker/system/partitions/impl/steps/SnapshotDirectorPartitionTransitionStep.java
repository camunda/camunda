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
import io.camunda.zeebe.broker.system.partitions.impl.ThreadSafeSnapshotDirector;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.util.sched.ActorCompatability;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public final class SnapshotDirectorPartitionTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (context.getSnapshotDirector() != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      final var director = context.getSnapshotDirector();
      context.getRaftPartition().getServer().removeCommittedEntryListener(director);
      try {
        director.close();
        context.setSnapshotDirector(null);
      } catch (final Exception e) {
        // ignored
      }
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if ((context.getSnapshotDirector() == null && targetRole != Role.INACTIVE)
        || shouldInstallOnTransition(targetRole, context.getCurrentRole())) {
      final var actorCompatability = new ActorCompatability();
      context.getActorSchedulingService().submitActor(actorCompatability);

      final var snapshotPeriod = context.getBrokerCfg().getData().getSnapshotPeriod();
      final StreamProcessorMode mode;
      if (targetRole == Role.LEADER) {
        mode = StreamProcessorMode.PROCESSING;
      } else {
        mode = StreamProcessorMode.REPLAY;
      }
      context.setSnapshotDirector(
          new ThreadSafeSnapshotDirector(
              actorCompatability,
              context.getStateController(),
              context.getStreamProcessor(),
              mode,
              snapshotPeriod));
    }
    return CompletableActorFuture.completed(null);
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
