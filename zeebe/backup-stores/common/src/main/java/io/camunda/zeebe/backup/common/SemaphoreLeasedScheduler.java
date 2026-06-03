/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public final class SemaphoreLeasedScheduler {

  private SemaphoreLeasedScheduler() {}

  /**
   * Schedules a task to be executed asynchronously, respecting the concurrency limit imposed by the
   * semaphore.
   *
   * @param task the task to execute
   * @param executor the executor to run the task
   * @param concurrencyLimit the semaphore to limit concurrency
   * @param <T> the type of the result
   * @return a future that completes with the result of the task
   */
  public static <T> CompletableFuture<T> schedule(
      final Supplier<T> task, final Executor executor, final Semaphore concurrencyLimit) {
    return scheduleAsync(
        () -> CompletableFuture.completedFuture(task.get()), executor, concurrencyLimit);
  }

  /**
   * Schedules an asynchronous task to be executed, respecting the concurrency limit imposed by the
   * semaphore. The permit is held until the future returned by the task completed.
   *
   * @param task the task to execute
   * @param executor the executor to run the task
   * @param concurrencyLimit the semaphore to limit concurrency
   * @param <T> the type of the result
   * @return a future that completes when the task's future completes
   */
  public static <T> CompletableFuture<T> scheduleAsync(
      final Supplier<CompletableFuture<T>> task,
      final Executor executor,
      final Semaphore concurrencyLimit) {
    final var result = new CompletableFuture<T>();

    executor.execute(
        () -> {
          try {
            concurrencyLimit.acquire();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(e);
            // The permit was never acquired
            return;
          }

          try {
            task.get()
                .whenComplete(
                    (res, err) -> {
                      try {
                        if (err != null) {
                          result.completeExceptionally(err);
                        } else {
                          result.complete(res);
                        }
                      } finally {
                        concurrencyLimit.release();
                      }
                    });
          } catch (final Exception e) {
            result.completeExceptionally(e);
            concurrencyLimit.release();
          }
        });

    return result;
  }
}
