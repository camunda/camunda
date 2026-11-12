/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static java.util.Objects.requireNonNull;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Runs a step only while the partition is the leader. For any other role, the step is told that the
 * partition becomes inactive: it closes what it opened as the leader and opens nothing.
 *
 * <p>When the partition state is kept in a relational database, only the leader opens it, so the
 * steps that need the state must not run on followers and candidates.
 *
 * <p>The step still sees the real current role in the context. That works for steps that open their
 * component whenever it is missing, as they all do: without a component, a follower or candidate
 * that becomes the leader opens one.
 */
@NullMarked
public final class LeaderOnlyTransitionStep implements PartitionTransitionStep {

  private final PartitionTransitionStep step;

  public LeaderOnlyTransitionStep(final PartitionTransitionStep step) {
    this.step = requireNonNull(step);
  }

  @Override
  public void onNewRaftRole(final PartitionTransitionContext context, final Role newRole) {
    step.onNewRaftRole(context, leaderOnly(newRole));
  }

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    return step.prepareTransition(context, term, leaderOnly(targetRole));
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    return step.transitionTo(context, term, leaderOnly(targetRole));
  }

  @Override
  public String getName() {
    return step.getName();
  }

  private static Role leaderOnly(final Role role) {
    return role == Role.LEADER ? Role.LEADER : Role.INACTIVE;
  }
}
