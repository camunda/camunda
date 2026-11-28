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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Schedules backoff delays on a timer thread that is shared by all clients of the JVM. The thread
 * ends when no delay is pending, so there is nothing to shut down when a client is closed.
 *
 * <p>The timer thread only waits: the tasks themselves run on the common pool, so that a callback
 * that blocks cannot hold up the delays of other requests.
 */
final class SharedTimerScheduler implements BackpressureScheduler {

  static final SharedTimerScheduler INSTANCE = new SharedTimerScheduler();

  private static final String TIMER_THREAD_NAME = "camunda-client-backpressure";
  private static final long TIMER_IDLE_TIMEOUT_SECONDS = 10;

  private final ScheduledThreadPoolExecutor timer;

  private SharedTimerScheduler() {
    timer =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              final Thread thread = new Thread(runnable, TIMER_THREAD_NAME);
              thread.setDaemon(true);
              return thread;
            });
    timer.setKeepAliveTime(TIMER_IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    timer.allowCoreThreadTimeOut(true);
    // most delays are cancelled before they are due, and must not stay queued until then
    timer.setRemoveOnCancelPolicy(true);
  }

  @Override
  public Cancellable schedule(final Runnable task, final long delayMs) {
    final ScheduledFuture<?> scheduled =
        timer.schedule(
            () -> CompletableFuture.runAsync(task), Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    return () -> scheduled.cancel(false);
  }
}
