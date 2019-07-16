/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.future;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FirstSuccessfullyCompletedFutureConsumer<T> implements BiConsumer<T, Throwable> {
  private final BiConsumer<T, Throwable> callback;
  private final Consumer<T> closer;
  private boolean isCompleted = false;
  private int pendingFutures;

  public FirstSuccessfullyCompletedFutureConsumer(
      int pendingFutures, BiConsumer<T, Throwable> callback, Consumer<T> closer) {
    this.pendingFutures = pendingFutures;
    this.callback = callback;
    this.closer = closer;
  }

  @Override
  public void accept(T result, Throwable failure) {
    pendingFutures -= 1;

    if (failure == null) {
      if (!isCompleted) {
        isCompleted = true;

        callback.accept(result, null);
      } else if (closer != null) {
        closer.accept(result);
      }
    } else {
      if (pendingFutures == 0) {
        callback.accept(null, failure);
      }
    }
  }
}
