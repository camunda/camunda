/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.concurrent;

import static com.google.common.base.Preconditions.checkState;
import static io.atomix.utils.concurrent.Threads.namedThreads;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single threaded context.
 *
 * <p>This is a basic {@link ThreadContext} implementation that uses a {@link
 * ScheduledExecutorService} to schedule events on the context thread.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class SingleThreadContext extends AbstractThreadContext {
  protected static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadContext.class);
  private static final Consumer<Throwable> DEFAULT_UNCAUGHT_EXCEPTION_OBSERVER =
      e -> LOGGER.error("An uncaught exception occurred", e);
  protected final ScheduledExecutorService executor;
  private final Consumer<Throwable> uncaughtExceptionObserver;
  private final Executor wrappedExecutor =
      new Executor() {
        @Override
        public void execute(final Runnable command) {
          try {
            executor.execute(command);
          } catch (final RejectedExecutionException e) {
            LOGGER.warn("Execution of {} was rejected!", command, e);
          }
        }
      };

  /**
   * Creates a new single thread context.
   *
   * <p>The provided context name will be passed to {@link AtomixThreadFactory} and used when
   * instantiating the context thread.
   *
   * @param nameFormat The context nameFormat which will be formatted with a thread number.
   */
  public SingleThreadContext(final String nameFormat) {
    this(namedThreads(nameFormat, LOGGER));
  }

  /**
   * Creates a new single thread context.
   *
   * <p>The provided context name will be passed to {@link AtomixThreadFactory} and used when
   * instantiating the context thread.
   *
   * @param nameFormat The context nameFormat which will be formatted with a thread number.
   * @param uncaughtExceptionObserver A consumer to observe exceptions thrown by submitted tasks
   */
  public SingleThreadContext(
      final String nameFormat, final Consumer<Throwable> uncaughtExceptionObserver) {
    this(namedThreads(nameFormat, LOGGER), uncaughtExceptionObserver);
  }

  /**
   * Creates a new single thread context.
   *
   * @param factory The thread factory.
   */
  public SingleThreadContext(final ThreadFactory factory) {
    this(new ScheduledThreadPoolExecutor(1, factory), DEFAULT_UNCAUGHT_EXCEPTION_OBSERVER);
  }

  /**
   * Creates a new single thread context.
   *
   * @param factory The thread factory.
   * @param uncaughtExceptionObserver A consumer to observe exceptions thrown by submitted tasks.
   */
  public SingleThreadContext(
      final ThreadFactory factory, final Consumer<Throwable> uncaughtExceptionObserver) {
    this(new ScheduledThreadPoolExecutor(1, factory), uncaughtExceptionObserver);
  }

  /**
   * Creates a new single thread context.
   *
   * @param executor The executor on which to schedule events. This must be a single thread
   *     scheduled executor.
   * @param uncaughtExceptionObserver A consumer to observe exceptions thrown by submitted tasks.
   */
  protected SingleThreadContext(
      final ScheduledExecutorService executor,
      final Consumer<Throwable> uncaughtExceptionObserver) {
    this(getThread(executor), executor, uncaughtExceptionObserver);
  }

  private SingleThreadContext(
      final Thread thread,
      final ScheduledExecutorService executor,
      final Consumer<Throwable> uncaughtExceptionObserver) {
    this.executor = executor;
    this.uncaughtExceptionObserver = uncaughtExceptionObserver;
    checkState(thread instanceof AtomixThread, "not a Catalyst thread");
    ((AtomixThread) thread).setContext(this);
  }

  /** Gets the thread from a single threaded executor service. */
  protected static AtomixThread getThread(final ExecutorService executor) {
    final AtomicReference<AtomixThread> thread = new AtomicReference<>();
    try {
      executor
          .submit(
              () -> {
                thread.set((AtomixThread) Thread.currentThread());
              })
          .get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new IllegalStateException("failed to initialize thread state", e);
    }
    return thread.get();
  }

  @Override
  public void execute(final Runnable command) {
    wrappedExecutor.execute(new WrappedRunnable(command));
  }

  @Override
  public Scheduled schedule(final Duration delay, final Runnable runnable) {
    final ScheduledFuture<?> future =
        executor.schedule(new WrappedRunnable(runnable), delay.toMillis(), TimeUnit.MILLISECONDS);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public Scheduled schedule(
      final Duration delay, final Duration interval, final Runnable runnable) {
    final ScheduledFuture<?> future =
        executor.scheduleAtFixedRate(
            new WrappedRunnable(runnable),
            delay.toMillis(),
            interval.toMillis(),
            TimeUnit.MILLISECONDS);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  class WrappedRunnable implements Runnable {

    private final Runnable command;

    WrappedRunnable(final Runnable command) {
      this.command = command;
    }

    @Override
    public void run() {
      try {
        command.run();
      } catch (final Exception e) {
        uncaughtExceptionObserver.accept(e);
      } catch (final Throwable e) {
        // If we don't handle throwable here, it will be swallowed by ScheduledThreadPoolExecutor
        uncaughtExceptionObserver.accept(e);
        throw e; // rethrow so that the ScheduledFuture is completed exceptionally
      }
    }
  }
}
