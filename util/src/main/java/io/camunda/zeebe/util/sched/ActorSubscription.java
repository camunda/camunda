/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;

/** Subscription to some external source of work / jobs. */
public interface ActorSubscription {
  /** returns true if the subscription should be able to trigger in the provided phase */
  default boolean triggersInPhase(final ActorLifecyclePhase phase) {
    return phase == ActorLifecyclePhase.STARTED;
  }

  /** called by the {@link ActorThread} to determine whether the subscription has work available. */
  boolean poll();

  /**
   * called by the {@link ActorThread} after {@link #poll()} returned true to get the job to be run
   */
  ActorJob getJob();

  /**
   * Returns true in case the subscription is recurring (ie. after the job finished, the
   * subscription is re-created
   */
  boolean isRecurring();

  /** callback received as the job returned by {@link #getJob()} completes execution. */
  default void onJobCompleted() {
    // default is ignore, can be implemented by cyclic / recurring subscriptions
  }

  default void cancel() {
    // nothing to do
  }
}
