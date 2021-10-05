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
 *
 * <p>The sequence of method calls is as follows:
 *
 * <ol>
 *   <li>onNewRaftRole(..) - called always as soon as a raft role change is announced.
 *   <li>prepareTransition(..) - called on all steps that executed during the last transition; steps
 *       will be called in reverse order
 *   <li>transitionTo(...) - called on all steps to perform the actual transition
 * </ol>
 *
 * <p>Note that a transition may be interrupted at any time. To that end, {@code onNewRaftRole(..)}
 * can be called at any time and can/should be used as a trigger to cancel the current step. The
 * other methods are called in order. An ongoing transition will only be aborted in between steps,
 * not while a step is running. Also, any subsequent steps will execute only after the currently
 * active step has completed
 */
public interface PartitionTransitionStep {

  /**
   * This method is called immediately after the new Raft role is known. It is expected that this
   * method completes instantly. It is called on all steps.
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
   * @param context
   * @param newRole target role to transition to
   */
  default void onNewRaftRole(final PartitionTransitionContext context, final Role newRole) {}

  /**
   * This method is a hook to prepare steps for a pending transition. This method is deprecated
   * because eventually we want ro remove it. Once removed, all steps need to take the necessary
   * preparatory steps as part of {@code newRaftRole(...)}.
   *
   * <p>For a time being, however, this method will be supported. Steps will be called in reverse
   * order and are expected to take any steps to assume a neutral stance
   */
  @Deprecated
  ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole);

  /** This method is called to start the actual transition */
  ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole);

  /** @return A log-friendly identification of the PartitionTransitionStep. */
  String getName();
}
