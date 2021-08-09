/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Deprecated
public class PartitionStepMigrationHelper {

  public static PartitionStep fromBootstrapStep(final PartitionStartupStep bootstrapStep) {
    return new WrappedPartitionBootstrapStep(bootstrapStep);
  }

  public static PartitionStep fromTransitionStep(final PartitionTransitionStep transitionStep) {
    return new WrappedPartitionTransitionStep(transitionStep);
  }

  private static class WrappedPartitionBootstrapStep implements PartitionStep {

    private final PartitionStartupStep bootstrapStep;

    public WrappedPartitionBootstrapStep(final PartitionStartupStep bootstrapStep) {
      this.bootstrapStep = bootstrapStep;
    }

    @Override
    public ActorFuture<Void> open(final PartitionBoostrapAndTransitionContextImpl context) {
      return wrapInVoidFuture(bootstrapStep::startup, context);
    }

    @Override
    public ActorFuture<Void> close(final PartitionBoostrapAndTransitionContextImpl context) {

      return wrapInVoidFuture(bootstrapStep::shutdown, context);
    }

    @Override
    public String getName() {
      return bootstrapStep.getName();
    }

    private ActorFuture<Void> wrapInVoidFuture(
        final BiConsumer<
                PartitionStartupContext, Consumer<Either<Throwable, PartitionStartupContext>>>
            wrappable,
        final PartitionStartupContext context) {
      final var result = new CompletableActorFuture<Void>();

      wrappable.accept(
          context,
          either -> {
            either.ifRightOrLeft(
                returnedContext -> result.complete(null), result::completeExceptionally);
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
