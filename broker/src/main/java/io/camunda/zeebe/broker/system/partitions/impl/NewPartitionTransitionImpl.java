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
  private PartitionTransitionProcess lastTransition;
  // these two should be set/cleared in tandem
  private PartitionTransitionProcess currentTransition;
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
    LOG.info(format("Transition to %s on term %d requested.", role, term));

    // notify steps immediately that a transition is coming; steps are encouraged to cancel any
    // ongoing activity at this point in time
    steps.forEach(step -> step.onNewRaftRole(role));

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
                (nil, error) -> cleanupLastTransition(nextTransitionFuture, term, role));

          } else {
            cleanupLastTransition(nextTransitionFuture, term, role);
          }
        });
    return nextTransitionFuture;
  }

  private void cleanupLastTransition(
      final ActorFuture<Void> nextTransitionFuture, final long term, final Role role) {
    if (lastTransition == null) {
      startNewTransition(nextTransitionFuture, term, role);
    } else {
      final var cleanupFuture = lastTransition.cleanup(term, role);
      concurrencyControl.runOnCompletion(
          cleanupFuture,
          (nil, error) -> {
            if (error != null) {
              LOG.error(
                  String.format("Error during transition clean up: %s", error.getMessage()), error);
              LOG.info(
                  String.format("Aborting transition to %s on term %d due to error.", role, term));
              nextTransitionFuture.completeExceptionally(error);
            } else {
              startNewTransition(nextTransitionFuture, term, role);
            }
          });
    }
  }

  private void startNewTransition(
      final ActorFuture<Void> nextTransitionFuture, final long term, final Role role) {
    currentTransition =
        new PartitionTransitionProcess(steps, concurrencyControl, context, term, role);
    currentTransitionFuture = nextTransitionFuture;
    concurrencyControl.runOnCompletion(
        currentTransitionFuture,
        (nil, error) -> {
          lastTransition = currentTransition;
          currentTransition = null;
          currentTransitionFuture = null;
        });
    currentTransition.start(currentTransitionFuture);
  }
}
