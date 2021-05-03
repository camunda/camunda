/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.retry;

import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.BooleanSupplier;

public final class ActorRetryMechanism {

  private final ActorControl actor;

  private OperationToRetry currentCallable;
  private BooleanSupplier currentTerminateCondition;
  private ActorFuture<Boolean> currentFuture;

  public ActorRetryMechanism(final ActorControl actor) {
    this.actor = actor;
  }

  void wrap(
      final OperationToRetry callable,
      final BooleanSupplier condition,
      final ActorFuture<Boolean> resultFuture) {
    currentCallable = callable;
    currentTerminateCondition = condition;
    currentFuture = resultFuture;
  }

  void run() throws Exception {
    if (currentCallable.run()) {
      currentFuture.complete(true);
      actor.done();
    } else if (currentTerminateCondition.getAsBoolean()) {
      currentFuture.complete(false);
      actor.done();
    } else {
      actor.yield();
    }
  }
}
