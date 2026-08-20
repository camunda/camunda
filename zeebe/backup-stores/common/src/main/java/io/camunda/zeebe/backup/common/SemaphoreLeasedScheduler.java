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
   * Schedules a (blocking) task to be executed on the given executor, respecting the concurrency
   * limit imposed by the semaphore.
   *
   * <p>Semaphore acquire, executing task and release all run in a single submitted unit of work:
   * the worker thread acquires a permit, runs the task, and releases the permit in a {@code
   * finally} block on that same thread. The release itself never needs an additional thread, so a
   * permit freed by a finishing task immediately unblocks a worker parked in {@link
   * Semaphore#acquire()}.
   *
   * <p><strong>Executor sizing:</strong> the scheduling itself is safe with a bounded executor.
   * However, if the {@code task} blocks on further work submitted back to the <em>same</em>
   * executor (e.g. it submits sub-tasks and joins on them), that executor must be unbounded (such
   * as a virtual-thread-per-task executor). With a bounded executor in that case all worker threads
   * can be parked waiting on tasks that can never be scheduled, which deadlocks. All current
   * callers use unbounded virtual-thread executors for this reason.
   *
   * @param task the task to execute
   * @param executor the executor to run the task (see the executor sizing note above)
   * @param concurrencyLimit the semaphore to limit concurrency
   * @param <T> the type of the result
   * @return a future that completes with the result of the task
   */
  public static <T> CompletableFuture<T> schedule(
      final Supplier<T> task, final Executor executor, final Semaphore concurrencyLimit) {
    final var result = new CompletableFuture<T>();

    executor.execute(
        () -> {
          try {
            concurrencyLimit.acquire();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(e);
            // The permit was never acquired, so it must not be released.
            return;
          }

          try {
            result.complete(task.get());
          } catch (final Throwable t) {
            result.completeExceptionally(t);
          } finally {
            concurrencyLimit.release();
          }
        });

    return result;
  }
}
