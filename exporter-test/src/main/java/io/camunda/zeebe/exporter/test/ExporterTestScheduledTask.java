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
package io.camunda.zeebe.exporter.test;

import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A controllable, thread-safe implementation of {@link ScheduledTask}. Thread-safety is important
 * as exporters may cancel the task from a different thread, and it's not that difficult to
 * guarantee.
 *
 * <p>This implementation is meant to be used with {@link ExporterTestController}.
 */
@ThreadSafe
final class ExporterTestScheduledTask implements ScheduledTask, Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExporterTestScheduledTask.class);

  private final Lock executeLock = new ReentrantLock();
  private final Duration delay;
  private final Runnable task;

  private volatile boolean isExecuted;
  private volatile boolean isCanceled;

  ExporterTestScheduledTask(final Duration delay, final Runnable task) {
    this.delay = Objects.requireNonNull(delay, "must specify a task delay");
    this.task = Objects.requireNonNull(task, "must specify a task");
  }

  Duration getDelay() {
    return delay;
  }

  Runnable getTask() {
    return task;
  }

  boolean isCanceled() {
    return isCanceled;
  }

  boolean wasExecuted() {
    return isExecuted;
  }

  @Override
  public void run() {
    LockSupport.runWithLock(
        executeLock,
        this::execute,
        e -> LOGGER.debug("Interrupted while awaiting executionLock, will not run task", e));
  }

  @Override
  public void cancel() {
    LockSupport.runWithLock(
        executeLock,
        this::cancelTask,
        e -> LOGGER.debug("Interrupted while awaiting executionLock, will not cancel task", e));
  }

  @GuardedBy("executeLock")
  private void cancelTask() {
    if (isCanceled || isExecuted) {
      return;
    }

    isCanceled = true;
  }

  @GuardedBy("executeLock")
  private void execute() {
    if (isCanceled || isExecuted) {
      return;
    }

    task.run();
    isExecuted = true;
  }
}
