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

import io.atomix.cluster.MemberId;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ScheduledFutureImpl;
import io.atomix.utils.concurrent.ThreadContext;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class DeterministicSingleThreadContext implements ThreadContext {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeterministicSingleThreadContext.class);

  private final DeterministicScheduler deterministicScheduler;
  private final MemberId memberId;
  private volatile Consumer<Throwable> uncaughtExceptionHandler;
  private volatile BooleanSupplier storageFailed = () -> false;

  public DeterministicSingleThreadContext(
      final DeterministicScheduler executor, final MemberId memberId) {
    deterministicScheduler = executor;
    this.memberId = memberId;
  }

  /**
   * Sets the handler that a task's uncaught exception is passed to once the member's storage
   * failed, like a real thread context does with every uncaught exception: the exception most
   * likely stems from that failure. Any other uncaught exception still fails the test.
   */
  public void setUncaughtExceptionHandler(
      final Consumer<Throwable> handler, final BooleanSupplier storageFailed) {
    uncaughtExceptionHandler = handler;
    this.storageFailed = storageFailed;
  }

  public DeterministicScheduler getDeterministicScheduler() {
    return deterministicScheduler;
  }

  public static ThreadContext createContext(final MemberId memberId) {
    return new DeterministicSingleThreadContext(new DeterministicScheduler(), memberId);
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

  private final class WrappedRunnable implements Runnable {

    private final Runnable command;

    WrappedRunnable(final Runnable command) {
      this.command = command;
    }

    @Override
    public void run() {
      try (final var ignored = MDC.putCloseable("actor-scheduler", memberId.toString())) {
        command.run();
      } catch (final Exception e) {
        final var handler = uncaughtExceptionHandler;
        if (handler != null && storageFailed.getAsBoolean()) {
          handler.accept(e);
          return;
        }
        LOGGER.error("Uncaught exception", e);
        Assertions.fail("Uncaught exception" + e);
      }
    }
  }
}
