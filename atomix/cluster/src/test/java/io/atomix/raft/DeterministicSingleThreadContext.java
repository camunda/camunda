/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft;

import static org.junit.Assert.fail;

import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ScheduledFutureImpl;
import io.atomix.utils.concurrent.ThreadContext;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jmock.lib.concurrent.DeterministicScheduler;

public final class DeterministicSingleThreadContext implements ThreadContext {

  private final DeterministicScheduler deterministicScheduler;

  private DeterministicSingleThreadContext(final DeterministicScheduler executor) {
    deterministicScheduler = executor;
  }

  public DeterministicScheduler getDeterministicScheduler() {
    return deterministicScheduler;
  }

  public static ThreadContext createContext() {
    return new DeterministicSingleThreadContext(new DeterministicScheduler());
  }

  @Override
  public Scheduled schedule(final long delay, final TimeUnit timeUnit, final Runnable command) {
    final var future =
        deterministicScheduler.schedule(new WrappedRunnable(command), delay, timeUnit);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public Scheduled schedule(final Duration delay, final Runnable command) {
    final var future =
        deterministicScheduler.schedule(
            new WrappedRunnable(command), delay.toMillis(), TimeUnit.MILLISECONDS);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public Scheduled schedule(
      final long initialDelay,
      final long interval,
      final TimeUnit timeUnit,
      final Runnable command) {
    final ScheduledFuture<?> future =
        deterministicScheduler.scheduleAtFixedRate(
            new WrappedRunnable(command), initialDelay, interval, timeUnit);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public Scheduled schedule(
      final Duration initialDelay, final Duration interval, final Runnable command) {
    final ScheduledFuture<?> future =
        deterministicScheduler.scheduleAtFixedRate(
            new WrappedRunnable(command),
            initialDelay.toMillis(),
            interval.toMillis(),
            TimeUnit.MILLISECONDS);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public void execute(final Runnable command) {
    deterministicScheduler.execute(new WrappedRunnable(command));
  }

  @Override
  public void checkThread() {
    // always assume running on the right context
  }

  @Override
  public void close() {
    // do nothing
  }

  static final class WrappedRunnable implements Runnable {

    private final Runnable command;

    WrappedRunnable(final Runnable command) {
      this.command = command;
    }

    @Override
    public void run() {
      try {
        command.run();
      } catch (final Exception e) {
        e.printStackTrace();
        fail("Uncaught exception");
      }
    }
  }
}
