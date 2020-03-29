/*
 * Copyright 2015-present Open Networking Foundation
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread pool context.
 *
 * <p>This is a special {@link ThreadContext} implementation that schedules events to be executed on
 * a thread pool. Events executed by this context are guaranteed to be executed on order but may be
 * executed on different threads in the provided thread pool.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class ThreadPoolContext extends AbstractThreadContext {
  private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolContext.class);
  protected final ScheduledExecutorService parent;
  private final Runnable runner;
  private final LinkedList<Runnable> tasks = new LinkedList<>();
  private boolean running;
  private final Executor executor =
      new Executor() {
        @Override
        public void execute(final Runnable command) {
          synchronized (tasks) {
            tasks.add(command);
            if (!running) {
              running = true;
              parent.execute(runner);
            }
          }
        }
      };

  /**
   * Creates a new thread pool context.
   *
   * @param parent The thread pool on which to execute events.
   */
  public ThreadPoolContext(final ScheduledExecutorService parent) {
    this.parent = checkNotNull(parent, "parent cannot be null");

    // This code was shamelessly stolededed from Vert.x:
    // https://github.com/eclipse/vert.x/blob/master/src/main/java/io/vertx/core/impl/OrderedExecutorFactory.java
    runner =
        () -> {
          ((AtomixThread) Thread.currentThread()).setContext(this);
          for (; ; ) {
            final Runnable task;
            synchronized (tasks) {
              task = tasks.poll();
              if (task == null) {
                running = false;
                return;
              }
            }

            try {
              task.run();
            } catch (final Throwable t) {
              LOGGER.error("An uncaught exception occurred", t);
              throw t;
            }
          }
        };
  }

  @Override
  public void execute(final Runnable command) {
    executor.execute(command);
  }

  @Override
  public Scheduled schedule(final Duration delay, final Runnable runnable) {
    final ScheduledFuture<?> future =
        parent.schedule(() -> executor.execute(runnable), delay.toMillis(), TimeUnit.MILLISECONDS);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public Scheduled schedule(
      final Duration delay, final Duration interval, final Runnable runnable) {
    final ScheduledFuture<?> future =
        parent.scheduleAtFixedRate(
            () -> executor.execute(runnable),
            delay.toMillis(),
            interval.toMillis(),
            TimeUnit.MILLISECONDS);
    return new ScheduledFutureImpl<>(future);
  }

  @Override
  public void close() {
    // Do nothing.
  }
}
