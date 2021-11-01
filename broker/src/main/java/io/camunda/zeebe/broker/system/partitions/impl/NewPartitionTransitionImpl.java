/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.util.Objects.requireNonNull;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionTransition;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public final class NewPartitionTransitionImpl implements PartitionTransition {
  private static final int INACTIVE_TERM = -1;
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<PartitionTransitionStep> steps;
  private PartitionTransitionContext context;
  private ConcurrencyControl concurrencyControl;
  private PartitionTransitionProcess lastTransition;

  // transient state - to keep track of the current transitions
  private PartitionTransitionProcess currentTransition;
  private ActorFuture<Void> currentTransitionFuture;

  public NewPartitionTransitionImpl(
      final List<PartitionTransitionStep> steps, final PartitionTransitionContext context) {
    this.steps = new ArrayList<>(requireNonNull(steps));
    this.context = requireNonNull(context);
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
  public ActorFuture<Void> toInactive() {
    return transitionTo(INACTIVE_TERM, Role.INACTIVE);
  }

  @Override
  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
  }

  @Override
  public void updateTransitionContext(final PartitionTransitionContext transitionContext) {
    context = transitionContext;
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
                // term and role should only bet set after the transition is completed, since on
                // clean up
                // we expect old term and role to make decision based on that
                if (error == null) {
                  context.setCurrentTerm(term);
                  context.setCurrentRole(role);
                }
                lastTransition = nextTransition;
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
      final var cleanupFuture = nextTransition.cleanup(term, role);
      cleanupFuture.onComplete(
          (ok, error) -> {
            if (error != null) {
              LOG.error("Error during transition clean up: {}", error.getMessage(), error);
              LOG.info("Aborting transition to {} on term {} due to error.", role, term);
              nextTransitionFuture.completeExceptionally(error);
            } else {
              nextTransition.start(nextTransitionFuture);
            }
          });
    }
  }
}
