/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.partitions.PartitionContext;
import io.zeebe.broker.system.partitions.PartitionStep;
import io.zeebe.broker.system.partitions.PartitionTransition;
import io.zeebe.util.exception.UnrecoverableException;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public class PartitionTransitionImpl implements PartitionTransition {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final List<PartitionStep> EMPTY_LIST = Collections.emptyList();
  private static final int INACTIVE_TERM = -1;

  private final PartitionContext context;
  private final List<PartitionStep> leaderSteps;
  private final List<PartitionStep> followerSteps;
  private final List<PartitionStep> openedSteps = new ArrayList<>();
  private CompletableActorFuture<Void> currentTransition = CompletableActorFuture.completed(null);

  public PartitionTransitionImpl(
      final PartitionContext context,
      final List<PartitionStep> leaderSteps,
      final List<PartitionStep> followerSteps) {
    this.context = context;
    this.leaderSteps = leaderSteps;
    this.followerSteps = followerSteps;
  }

  @Override
  public ActorFuture<Void> toFollower(final long currentTerm) {
    return enqueueTransition(currentTerm, followerSteps);
  }

  @Override
  public ActorFuture<Void> toLeader(final long currentTerm) {
    return enqueueTransition(currentTerm, leaderSteps);
  }

  @Override
  public ActorFuture<Void> toInactive() {
    return enqueueTransition(INACTIVE_TERM, EMPTY_LIST);
  }

  /**
   * This method allows to enqueue the next transition, such that the transitions are executed in
   * order. Previous we had the issue that all transitions have subscribe to the current transition,
   * which lead to undefined behavior.
   *
   * @param partitionStepList the steps which should be installed on the transition
   */
  private ActorFuture<Void> enqueueTransition(
      final long currentTerm, final List<PartitionStep> partitionStepList) {
    final var nextTransitionFuture = new CompletableActorFuture<Void>();
    final var nextCurrentTransition = currentTransition;
    currentTransition = nextTransitionFuture;
    nextCurrentTransition.onComplete(
        (nothing, err) -> transition(currentTerm, nextTransitionFuture, partitionStepList));
    return nextTransitionFuture;
  }

  private void transition(
      final long currentTerm,
      final CompletableActorFuture<Void> future,
      final List<PartitionStep> steps) {
    closePartition()
        .onComplete(
            (nothing, err) -> {
              if (err instanceof UnrecoverableException) {
                future.completeExceptionally(err);
              } else {
                installPartition(currentTerm, future, new ArrayList<>(steps));
              }
            });
  }

  private void installPartition(
      final long currentTerm,
      final CompletableActorFuture<Void> future,
      final List<PartitionStep> steps) {
    if (steps.isEmpty()) {
      LOG.debug(
          "Partition {} transition complete, installed {} resources!",
          context.getPartitionId(),
          openedSteps.size());
      future.complete(null);
      return;
    }

    final PartitionStep step = steps.remove(0);
    try {
      step.open(currentTerm, context)
          .onComplete(
              (value, err) -> {
                if (err != null) {
                  LOG.error("Expected to open step '{}' but failed with", step.getName(), err);
                  tryCloseStep(step);
                  future.completeExceptionally(err);
                } else {
                  openedSteps.add(step);
                  installPartition(currentTerm, future, steps);
                }
              });
    } catch (final Exception e) {
      LOG.error("Expected to open step '{}' but failed with", step.getName(), e);
      tryCloseStep(step);
      future.completeExceptionally(e);
    }
  }

  private void tryCloseStep(final PartitionStep step) {
    // close if there's anything to close. Don't add to 'opened' list, since the open did not
    // complete, the close might also fail but that shouldn't prevent the next transition
    try {
      step.close(context);
    } catch (final Exception e) {
      LOG.debug("Couldn't close partition step '{}' that failed to open", step.getName(), e);
    }
  }

  private CompletableActorFuture<Void> closePartition() {
    final var closingSteps = new ArrayList<>(openedSteps);
    Collections.reverse(closingSteps);
    return closeSteps(closingSteps);
  }

  private CompletableActorFuture<Void> closeSteps(final List<PartitionStep> steps) {
    final var closingPartitionFuture = new CompletableActorFuture<Void>();
    closeNextStep(closingPartitionFuture, steps, null);
    return closingPartitionFuture;
  }

  private void closeNextStep(
      final CompletableActorFuture<Void> future,
      final List<PartitionStep> steps,
      final Throwable throwable) {
    if (steps.isEmpty()) {
      LOG.debug(
          "Partition {} closed all previous open resources, before transitioning.",
          context.getPartitionId());
      if (throwable == null) {
        future.complete(null);
      } else {
        future.completeExceptionally(throwable);
      }
      return;
    }

    final PartitionStep step = steps.remove(0);
    LOG.debug("Closing Zeebe-Partition-{}: {}", context.getPartitionId(), step.getName());

    try {
      step.close(context)
          .onComplete(
              (v, closingError) -> {
                if (closingError == null) {
                  LOG.debug(
                      "Closing Zeebe-Partition-{}: {} closed successfully",
                      context.getPartitionId(),
                      step.getName());
                } else {
                  LOG.error(
                      "Closing Zeebe-Partition-{}: {} failed to close. Closing remaining steps",
                      context.getPartitionId(),
                      step.getName(),
                      closingError);
                }

                openedSteps.remove(step);
                closeNextStep(future, steps, throwable != null ? throwable : closingError);
              });
    } catch (final Exception e) {
      LOG.error(
          "Zeebe-Partition-{}: Step {} failed to close with uncaught exception",
          context.getPartitionId(),
          step.getName(),
          e);
      openedSteps.remove(step);
      closeNextStep(future, steps, e);
    }
  }
}
