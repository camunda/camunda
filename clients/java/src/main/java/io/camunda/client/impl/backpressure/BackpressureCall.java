/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.backpressure;

import io.camunda.client.impl.backpressure.BackpressureManager.Permit;
import io.camunda.client.impl.backpressure.BackpressureScheduler.Cancellable;
import io.camunda.client.impl.backpressure.BackpressureSignals.Failure;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The backpressure handling of one request, from the moment it waits for its turn until it is done:
 * it holds the permit of the concurrency limit, counts the attempts and works out when the next
 * attempt may start.
 *
 * <p>The permit is held across all attempts and the delays between them, so a request that is being
 * retried keeps counting against the limit.
 *
 * <p>The request timeout is a budget for the whole request: waiting for the limit, every attempt
 * and every delay between attempts. A retry is only made if its delay fits into what is left of the
 * budget.
 */
public final class BackpressureCall {

  private final BackpressureController controller;
  private final boolean gated;
  private final long timeoutMillis;
  private final long deadlineMillis;
  private final int maxAttempts;
  private final AtomicBoolean released = new AtomicBoolean();

  private int attempt = 1; // guarded by this
  private volatile Permit permit;
  private volatile CompletableFuture<Permit> pendingPermit;

  BackpressureCall(
      final BackpressureController controller,
      final boolean gated,
      final long timeoutMillis,
      final int maxAttempts) {
    this.controller = controller;
    this.gated = gated;
    this.timeoutMillis = timeoutMillis;
    this.maxAttempts = maxAttempts;
    deadlineMillis = timeoutMillis > 0 ? controller.nowMillis() + timeoutMillis : Long.MAX_VALUE;
  }

  /**
   * Waits for the turn of this request. The future completes when the first attempt may start, and
   * fails if the request waited longer than its timeout or if too many requests are waiting. It
   * never blocks a thread.
   */
  public CompletableFuture<Void> acquire() {
    if (!gated) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<Permit> acquisition = controller.manager().acquire();
    pendingPermit = acquisition;
    if (released.get()) {
      // released before the acquisition was known: release() could not withdraw it
      acquisition.cancel(false);
    }
    // a request that does not have to wait, which is the usual case, needs no timeout
    if (deadlineMillis != Long.MAX_VALUE && !acquisition.isDone()) {
      final Cancellable timeout =
          controller
              .scheduler()
              .schedule(
                  () ->
                      acquisition.completeExceptionally(
                          new BackpressureTimeoutException(timeoutMillis)),
                  Math.max(0, remainingMillis()));
      acquisition.whenComplete((acquired, error) -> timeout.cancel());
    }

    final CompletableFuture<Void> result = new CompletableFuture<>();
    acquisition.whenComplete(
        (acquired, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
            return;
          }
          permit = acquired;
          if (released.get()) {
            // the request was given up while it waited
            acquired.release();
          }
          result.complete(null);
        });
    return result;
  }

  /** Records that the request succeeded. */
  public void onSuccess() {
    controller.manager().recordSuccess();
  }

  /**
   * Records that an attempt failed, and decides whether to try again.
   *
   * @param failure how the failure is classified
   * @param retryAfterMillis how long the cluster asked to wait before the next attempt, 0 if it did
   *     not ask
   * @return how long to wait before the next attempt, or -1 if the request is not to be tried again
   *     because the failure is permanent, because the attempts are used up, because the request was
   *     given up, or because the delay would not fit into the request timeout
   */
  public long onFailure(final Failure failure, final long retryAfterMillis) {
    if (failure == Failure.BACKPRESSURE) {
      controller.manager().recordBackpressure();
    }
    if (failure == Failure.PERMANENT || released.get()) {
      return -1;
    }

    final BackpressureSettings settings = controller.settings();
    synchronized (this) {
      if (attempt >= maxAttempts) {
        return -1;
      }
      // exponential backoff with full jitter: a random delay between 0 and the capped exponent
      final int exponent = Math.min(attempt - 1, 30);
      final long cap = Math.min(settings.baseDelayMs() << exponent, settings.maxDelayMs());
      long delay = (long) Math.floor(controller.nextRandom() * cap);
      delay = Math.max(delay, retryAfterMillis);
      if (delay >= remainingMillis()) {
        return -1;
      }
      attempt++;
      return delay;
    }
  }

  /** The time that is left of the request timeout, {@link Long#MAX_VALUE} if it has none. */
  public long remainingMillis() {
    return deadlineMillis == Long.MAX_VALUE
        ? Long.MAX_VALUE
        : deadlineMillis - controller.nowMillis();
  }

  /** Runs the task after the delay, without blocking a thread. */
  public void schedule(final Runnable task, final long delayMillis) {
    controller.scheduler().schedule(task, delayMillis);
  }

  /** Gives the permit back. Must be called when the request is done, whatever its outcome. */
  public void release() {
    if (!released.compareAndSet(false, true)) {
      return;
    }
    final CompletableFuture<Permit> pending = pendingPermit;
    if (pending != null) {
      // withdraws the request if it still waits; otherwise the permit is released below
      pending.cancel(false);
    }
    final Permit held = permit;
    if (held != null) {
      held.release();
    }
  }
}
