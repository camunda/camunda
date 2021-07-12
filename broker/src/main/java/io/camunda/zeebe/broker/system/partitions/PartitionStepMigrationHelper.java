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

  public static PartitionStep fromBootstrapStep(final PartitionBootstrapStep bootstrapStep) {
    return new WrappedPartitionBootstrapStep(bootstrapStep);
  }

  public static PartitionStep fromTransitionStep(final PartitionTransitionStep transitionStep) {
    return new WrappedPartitionTransitionStep(transitionStep);
  }

  private static class WrappedPartitionBootstrapStep implements PartitionStep {

    private final PartitionBootstrapStep bootstrapStep;

    public WrappedPartitionBootstrapStep(final PartitionBootstrapStep bootstrapStep) {
      this.bootstrapStep = bootstrapStep;
    }

    @Override
    public ActorFuture<Void> open(final PartitionBoostrapAndTransitionContextImpl context) {
      return wrapInVoidFuture(bootstrapStep.open(context));
    }

    @Override
    public ActorFuture<Void> close(final PartitionBoostrapAndTransitionContextImpl context) {

      return wrapInVoidFuture(bootstrapStep.close(context));
    }

    private ActorFuture<Void> wrapInVoidFuture(
        final ActorFuture<PartitionBootstrapContext> wrappable) {
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

    @Override
    public String getName() {
      return bootstrapStep.getName();
    }
  }

  private static class WrappedPartitionTransitionStep implements PartitionStep {

    private final PartitionTransitionStep transitionStep;

    public WrappedPartitionTransitionStep(final PartitionTransitionStep transitionStep) {
      this.transitionStep = transitionStep;
    }

    @Override
    public ActorFuture<Void> open(final PartitionBoostrapAndTransitionContextImpl context) {
      return transitionStep.transitionTo(
          context, context.getRaftPartition().term(), context.getRaftPartition().getRole());
    }

    @Override
    public ActorFuture<Void> close(final PartitionBoostrapAndTransitionContextImpl context) {
      return transitionStep.transitionTo(context, context.getRaftPartition().term(), Role.INACTIVE);
    }

    @Override
    public String getName() {
      return transitionStep.getName();
    }
  }
}
