/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.lang.String.format;
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
  // these two should be set/cleared in tandem
  private Transition currentTransition;
  private ActorFuture<Void> currentTransitionFuture;
  // these two should be set in tandem

  public NewPartitionTransitionImpl(
      final List<PartitionTransitionStep> steps, final PartitionTransitionContext context) {
    this.steps = new ArrayList<>(requireNonNull(steps));
    this.context = requireNonNull(context);
  }

  public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
  }

  public void updateTransitionContext(final PartitionTransitionContext transitionContext) {
    context = transitionContext;
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

  public ActorFuture<Void> transitionTo(final long term, final Role role) {
    // notify steps immediately that a transition is coming; steps are encouraged to cancel any
    // ongoing activity at this point in time
    steps.forEach(step -> step.prepareForTransition(role));

    final ActorFuture<Void> nextTransitionFuture = concurrencyControl.createFuture();

    concurrencyControl.submit(
        () -> {
          if (currentTransition != null) {
            LOG.info(
                format(
                    "Transition to %s on term %d requested while another transition is still running",
                    role, term));
            currentTransition.cancel(); // this will drop any subsequent transition steps

            // schedule new transition as soon as the current step of the current transition
            // has completed
            concurrencyControl.runOnCompletion(
                currentTransitionFuture,
                (nil, error) -> startNewTransition(nextTransitionFuture, term, role));

          } else {
            startNewTransition(nextTransitionFuture, term, role);
          }
        });
    return nextTransitionFuture;
  }

  private void startNewTransition(
      final ActorFuture<Void> nextTransitionFuture, final long term, final Role role) {
    currentTransition = new Transition(steps, concurrencyControl, context, term, role);
    currentTransitionFuture = nextTransitionFuture;
    concurrencyControl.runOnCompletion(
        currentTransitionFuture,
        (nil, error) -> {
          currentTransition = null;
          currentTransitionFuture = null;
        });
    currentTransition.start(currentTransitionFuture);
  }

  private static final class Transition {

    private final List<PartitionTransitionStep> pendingSteps;
    private final ConcurrencyControl concurrencyControl;
    private final PartitionTransitionContext context;
    private final long term;
    private final Role role;
    private boolean cancelRequested = false;

    private Transition(
        final List<PartitionTransitionStep> pendingSteps,
        final ConcurrencyControl concurrencyControl,
        final PartitionTransitionContext context,
        final long term,
        final Role role) {
      this.pendingSteps = new ArrayList<>(pendingSteps);
      this.concurrencyControl = concurrencyControl;
      this.context = context;
      this.term = term;
      this.role = role;
    }

    private void start(final ActorFuture<Void> future) {
      LOG.info(format("Transition to %s on term %d starting", role, term));

      if (pendingSteps.isEmpty()) {
        LOG.info("No steps defined for transition");
        future.complete(null);
        return;
      }

      proceed(future);
    }

    private void proceed(final ActorFuture<Void> future) {
      if (cancelRequested) {
        LOG.info(format("Cancelling transition to %s on term %d", role, term));
        future.complete(null);
        return;
      }

      concurrencyControl.submit(
          () -> {
            final var nextStep = pendingSteps.remove(0);

            LOG.info(
                format(
                    "Transition to %s on term %d - executing %s", role, term, nextStep.getName()));

            nextStep
                .transitionTo(context, term, role)
                .onComplete((nil, error) -> onStepCompletion(future, error));
          });
    }

    private void onStepCompletion(final ActorFuture<Void> future, final Throwable error) {
      if (error != null) {
        LOG.error(error.getMessage(), error);
        future.completeExceptionally(error);

        return;
      }

      if (pendingSteps.isEmpty()) {
        LOG.info(format("Transition to %s on term %d completed", role, term));
        future.complete(null);

        return;
      }

      proceed(future);
    }

    private void cancel() {
      LOG.info(format("Received cancel signal for transition to %s on term %d", role, term));
      cancelRequested = true;
    }
  }
}
