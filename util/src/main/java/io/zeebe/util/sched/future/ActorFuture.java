/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.future;

import io.zeebe.util.sched.ActorTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** interface for actor futures */
public interface ActorFuture<V> extends Future<V> {
  void complete(V value);

  void completeExceptionally(String failure, Throwable throwable);

  void completeExceptionally(Throwable throwable);

  V join();

  V join(long timeout, TimeUnit timeUnit);

  /** To be used by scheduler only */
  void block(ActorTask onCompletion);

  /**
   * Registers an consumer, which is executed after the future was completed. The consumer is
   * executed in the current actor thread, which is used to register the consumer.
   *
   * <p>Example:
   *
   * <p>Actor A calls Actor B to retrieve an value. Actor B returns an future, which will be
   * completed later with the right value. Actor A wants to do some work, after B returns the value.
   * For that Actor A calls `#onComplete`, at this returned future, to register an consumer. After
   * the future is completed, the registered consumer is called in the Actor A context.
   *
   * <p>Running in Actor A context:
   *
   * <pre>
   *  final ActorFuture<Value> future = ActorB.getValue();
   *  future.onComplete(value, throwable -> { // do things - runs in Actor A context again
   *  });
   * </pre>
   *
   * @param consumer the consumer which should be called after the future was completed
   * @throws UnsupportedOperationException when not called on actor thread
   */
  void onComplete(BiConsumer<V, Throwable> consumer);

  boolean isCompletedExceptionally();

  Throwable getException();
}
