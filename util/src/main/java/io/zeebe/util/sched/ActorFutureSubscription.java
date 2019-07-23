/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;
import io.zeebe.util.sched.future.ActorFuture;

public class ActorFutureSubscription implements ActorSubscription {
  private final ActorJob callbackJob;
  private final int phaseMask;
  private ActorFuture<?> future;

  public ActorFutureSubscription(ActorFuture<?> future, ActorJob callbackJob, int phaseMask) {
    this.future = future;
    this.callbackJob = callbackJob;
    this.phaseMask = phaseMask;
  }

  @Override
  public boolean triggersInPhase(ActorLifecyclePhase phase) {
    // triggers in all phases
    return phase != ActorLifecyclePhase.CLOSED && (phase.getValue() & phaseMask) > 0;
  }

  @Override
  public boolean poll() {
    return future.isDone();
  }

  @Override
  public ActorJob getJob() {
    return callbackJob;
  }

  @Override
  public boolean isRecurring() {
    return false;
  }
}
