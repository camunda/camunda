/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class AllCompletedFutureConsumer<T> implements BiConsumer<T, @Nullable Throwable> {
  private final Consumer<@Nullable Throwable> callback;
  private int pendingFutures;

  private @Nullable Throwable occuredFailure = null;

  public AllCompletedFutureConsumer(
      final int pendingFutures, final Consumer<@Nullable Throwable> callback) {
    this.pendingFutures = pendingFutures;
    this.callback = callback;
  }

  @Override
  public void accept(final T result, final @Nullable Throwable failure) {
    pendingFutures -= 1;

    if (failure != null) {
      occuredFailure = failure;
    }

    if (pendingFutures == 0) {
      callback.accept(occuredFailure);
    }
  }
}
