/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.future;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class AllCompletedFutureConsumer<T> implements BiConsumer<T, Throwable> {
  private final Consumer<Throwable> callback;
  private int pendingFutures;

  private Throwable occuredFailure = null;

  public AllCompletedFutureConsumer(final int pendingFutures, final Consumer<Throwable> callback) {
    this.pendingFutures = pendingFutures;
    this.callback = callback;
  }

  @Override
  public void accept(final T result, final Throwable failure) {
    pendingFutures -= 1;

    if (failure != null) {
      occuredFailure = failure;
    }

    if (pendingFutures == 0) {
      callback.accept(occuredFailure);
    }
  }
}
