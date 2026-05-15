/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

public final class ActorRetryMechanism {
  private @Nullable OperationToRetry currentCallable;
  private @Nullable BooleanSupplier currentTerminateCondition;
  private @Nullable ActorFuture<Boolean> currentFuture;

  void wrap(
      final OperationToRetry callable,
      final BooleanSupplier condition,
      final ActorFuture<Boolean> resultFuture) {
    currentCallable = callable;
    currentTerminateCondition = condition;
    currentFuture = resultFuture;
  }

  Control run() throws Exception {
    if (requireNonNull(currentCallable, "currentCallable").run()) {
      requireNonNull(currentFuture, "currentFuture").complete(true);
      return Control.DONE;
    } else if (requireNonNull(currentTerminateCondition, "currentTerminateCondition")
        .getAsBoolean()) {
      requireNonNull(currentFuture, "currentFuture").complete(false);
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
