/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

@Deprecated
public class PartitionStepMigrationHelper {

  public static PartitionStep fromStartupStep(final PartitionStartupStep startupStep) {
    return new WrappedPartitionStartupStep(startupStep);
  }

  public static PartitionStep fromTransitionStep(final PartitionTransitionStep transitionStep) {
    return new WrappedPartitionTransitionStep(transitionStep);
  }

  private static class WrappedPartitionStartupStep implements PartitionStep {

    private final PartitionStartupStep startupStep;

    public WrappedPartitionStartupStep(final PartitionStartupStep startupStep) {
      this.startupStep = startupStep;
    }

    @Override
    /** Must be called from an actor */
    public ActorFuture<Void> open(final PartitionStartupAndTransitionContextImpl context) {
      return wrapInVoidFuture(startupStep.startup(context));
    }

    @Override
    /** Must be called from an actor */
    public ActorFuture<Void> close(final PartitionStartupAndTransitionContextImpl context) {
      return wrapInVoidFuture(startupStep.shutdown(context));
    }

    @Override
    public String getName() {
      return startupStep.getName();
    }

    private ActorFuture<Void> wrapInVoidFuture(
        final ActorFuture<PartitionStartupContext> wrappable) {
      final var result = new CompletableActorFuture<Void>();

      wrappable.onComplete(
          (success, error) -> {
            if (error != null) {
              result.completeExceptionally(error);
            } else {
              result.complete(null);
            }
          });

      return result;
    }
  }

  private static class WrappedPartitionTransitionStep implements PartitionStep {

    private final PartitionTransitionStep transitionStep;

    public WrappedPartitionTransitionStep(final PartitionTransitionStep transitionStep) {
      this.transitionStep = transitionStep;
    }

    @Override
    public ActorFuture<Void> open(final PartitionStartupAndTransitionContextImpl context) {
      return transitionStep.transitionTo(
          context, context.getRaftPartition().term(), context.getRaftPartition().getRole());
    }

    @Override
    public ActorFuture<Void> close(final PartitionStartupAndTransitionContextImpl context) {
      return transitionStep.transitionTo(context, context.getRaftPartition().term(), Role.INACTIVE);
    }

    @Override
    public String getName() {
      return transitionStep.getName();
    }
  }
}
