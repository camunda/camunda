/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.future;

import java.util.function.BiConsumer;
import org.agrona.LangUtil;

public final class FutureContinuationRunnable<T> implements Runnable {
  private final ActorFuture<T> future;
  private final BiConsumer<T, Throwable> consumer;

  public FutureContinuationRunnable(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> consumer) {
    this.future = future;
    this.consumer = consumer;
  }

  @Override
  public void run() {
    if (!future.isCompletedExceptionally()) {
      try {
        final T res = future.get();
        consumer.accept(res, null);
      } catch (final Exception e) {
        LangUtil.rethrowUnchecked(e);
      }
    } else {
      consumer.accept(null, future.getException());
    }
  }
}
