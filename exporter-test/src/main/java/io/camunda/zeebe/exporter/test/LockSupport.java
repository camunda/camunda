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

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

/**
 * A utility class for common logic with locks. If this ends up being used in other places, consider
 * moving to the zeebe-util module.
 */
final class LockSupport {
  private LockSupport() {}

  /**
   * Convenience method to run a specific task with a lock held, and optionally react if acquiring
   * the lock is interrupted.
   *
   * <p>NOTE: when interrupted, the task is <em>not</em> ran.
   *
   * @param lock the lock to acquire
   * @param task the task to run
   * @param onInterrupted the callback if acquiring the lock is interrupted
   */
  static void runWithLock(
      final Lock lock, final Runnable task, @Nullable final Consumer<Exception> onInterrupted) {
    Objects.requireNonNull(lock, "must specify a lock to acquire");
    Objects.requireNonNull(task, "must specify a task to execute once the lock was acquired");

    try {
      lock.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      if (onInterrupted != null) {
        onInterrupted.accept(e);
      }

      return;
    }

    try {
      task.run();
    } finally {
      lock.unlock();
    }
  }
}
