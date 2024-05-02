/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.util.Objects.requireNonNull;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionTransition;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.health.HealthIssue;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public final class PartitionTransitionImpl implements PartitionTransition {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<PartitionTransitionStep> steps;
  private PartitionTransitionContext context;
  private ConcurrencyControl concurrencyControl;
  private PartitionTransitionProcess lastTransition;

  // transient state - to keep track of the current transitions
  private PartitionTransitionProcess currentTransition;
  private ActorFuture<Void> currentTransitionFuture;

  public PartitionTransitionImpl(final List<PartitionTransitionStep> steps) {
    this.steps = new ArrayList<>(requireNonNull(steps));
  }

  @Override
  public ActorFuture<Void> toFollower(final long term) {
    return transitionTo(term, Role.FOLLOWER);
  }

  @Override
  public ActorFuture<Void> toLeader(final long term) {
    return transitionTo(term, Role.LEADER);
  }

  @Override
  public ActorFuture<Void> toInactive(final long term) {
    return transitionTo(term, Role.INACTIVE);
  }

  @Override
  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
  }

  @Override
  public void updateTransitionContext(final PartitionTransitionContext transitionContext) {
    context = transitionContext;
  }

  @Override
  public HealthIssue getHealthIssue() {
    if (currentTransition != null) {
      return currentTransition.getHealthIssue();
    }
    return null;
  }

  public ActorFuture<Void> transitionTo(final long term, final Role role) {
    LOG.info("Transition to {} on term {} requested.", role, term);

    // notify steps immediately that a transition is coming; steps are encouraged to cancel any
    // ongoing activity at this point in time
    steps.forEach(step -> step.onNewRaftRole(context, role));

    final ActorFuture<Void> nextTransitionFuture = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          final var nextTransition =
              new PartitionTransitionProcess(steps, concurrencyControl, context, term, role);
          nextTransitionFuture.onComplete(
              (v, error) -> {
                lastTransition = nextTransition;

                if (!(error instanceof FailedPartitionTransitionPreparation)) {
                  // Prepare phase succeeded and the transition either completed successfully,
                  // failed or was cancelled. Either way, we update term and role to ensure that the
                  // next transition will go through the necessary prepare phase.
                  // If a `FailedPartitionTransitionPreparation` is actually thrown, the next
                  // transition must attempt the same preparation again which means we can't update
                  // term and role yet.

                  context.setCurrentTerm(term);
                  context.setCurrentRole(role);
                }
              });

          enqueueNextTransition(term, role, nextTransitionFuture, nextTransition);
        });

    return nextTransitionFuture;
  }

  /**
   * Enqueues the new next transition after the current ongoing transition. It will cancel the
   * ongoing transition to speed up the process of execution.
   */
  private void enqueueNextTransition(
      final long term,
      final Role role,
      final ActorFuture<Void> nextTransitionFuture,
      final PartitionTransitionProcess nextTransition) {
    final ActorFuture<Void> ongoingTransitionFuture;
    if (currentTransition == null) {
      // we run our first transition nothing to cancel nor to enqueue
      ongoingTransitionFuture = concurrencyControl.createCompletedFuture();
    } else {
      // we can be sure that the currentTransitionFuture is also set, since it is always set
      // with the currentTransition
      ongoingTransitionFuture = currentTransitionFuture;

      final var ongoingTransition = currentTransition;
      if (!ongoingTransition.isCompleted()) {
        LOG.info(
            "Cancelling transition {} in favor of next transition {}",
            ongoingTransition,
            nextTransition);
      }
      ongoingTransition.cancel();
    }

    // For safety reasons we have to immediately replace the current transition future with
    // the next transition future, such that we make sure that we enqueue all transitions
    // after another. We cancel current transitions to make the process faster.
    currentTransitionFuture = nextTransitionFuture;
    currentTransition = nextTransition;

    ongoingTransitionFuture.onComplete(
        (nothing, error) ->
            performNextTransition(term, role, nextTransitionFuture, nextTransition));
  }

  /**
   * Performs the next transition after the ongoing transition is completed. It will start to clean
   * resources of the previous transition before performing the next transition.
   */
  private void performNextTransition(
      final long term,
      final Role role,
      final ActorFuture<Void> nextTransitionFuture,
      final PartitionTransitionProcess nextTransition) {
    if (lastTransition == null) {
      nextTransition.start(nextTransitionFuture);
    } else {
      final var prepareFuture = nextTransition.prepare(term, role);
      prepareFuture.onComplete(
          (ok, error) -> {
            if (error != null) {
              LOG.error("Error during transition preparation: {}", error.getMessage(), error);
              LOG.info("Aborting transition to {} on term {} due to error.", role, term);
              nextTransitionFuture.completeExceptionally(
                  new FailedPartitionTransitionPreparation(error));
            } else {
              nextTransition.start(nextTransitionFuture);
            }
          });
    }
  }
}
