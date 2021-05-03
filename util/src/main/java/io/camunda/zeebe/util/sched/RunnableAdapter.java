/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class RunnableAdapter<T> implements Runnable {
  private final Callable<T> callable;

  private T value;
  private Throwable exception;

  public RunnableAdapter(final Callable<T> callable) {
    this.callable = callable;
  }

  @Override
  public void run() {
    try {
      value = callable.call();
    } catch (final Exception e) {
      exception = e;
    }
  }

  public static <T> RunnableAdapter<T> wrapCallable(final Callable<T> callable) {
    return new RunnableAdapter<>(callable);
  }

  public static RunnableAdapter<Void> wrapRunnable(final Runnable callable) {
    return new RunnableAdapter<Void>(
        () -> {
          callable.run();
          return null;
        });
  }

  public Runnable wrapBiConsumer(final BiConsumer<T, Throwable> consumer) {
    return () -> {
      consumer.accept(value, exception);
    };
  }

  public Runnable wrapConsumer(final Consumer<Throwable> consumer) {
    return () -> {
      consumer.accept(exception);
    };
  }
}
