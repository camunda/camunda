/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.retry;

import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
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

  Control run() throws Exception {
    if (currentCallable.run()) {
      currentFuture.complete(true);
      return Control.DONE;
    } else if (currentTerminateCondition.getAsBoolean()) {
      currentFuture.complete(false);
      return Control.DONE;
    } else {
      return Control.RETRY;
    }
  }

  enum Control {
    RETRY,
    DONE;
  }
}
