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

/**
 * A PartitionTransitionStep is an action to be taken while transitioning the partition to a new
 * role
 */
public interface PartitionTransitionStep {

  /**
   * This method is called prior to starting a new transition. It is called immediately after the
   * new Raft role is known. It is expected that this method completes instantly. It is called on
   * all steps, and only afterwards the first step is called with the transition.
   *
   * <p>Steps are expected to pause any active requests and assume a neutral stance after this
   * method is called. After all steps have been notified, the first steps' {@code
   * transitionTo(...)} will be called, and then subsequently all other steps. This means that
   * during the time between the call to thie method and the call to {@code transitionTo(...)} some
   * preceding steps may have already transitioned, but others are still waiting for transition.
   *
   * <p>To summarize, after this method is called, the partition is in an undefined state. And as
   * soon as {@code transitionTo(...)} is called the partition has completed all transition steps up
   * to this point
   *
   * <p>Note, that this may also be called while a transition is currently running, for example if
   * the raft partition transitions faster than the Zeebe partition. In this case steps are
   * encouraged to cancel what they are doing
   *
   * @param newRole target role to transition to
   */
  default void onNewRaftRole(final Role newRole) {}

  ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole);

  /** @return A log-friendly identification of the PartitionTransitionStep. */
  String getName();
}
