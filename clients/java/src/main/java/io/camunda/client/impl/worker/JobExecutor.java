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
package io.camunda.client.impl.worker;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * The executor a job worker hands its jobs to, with the things the worker needs on top of a plain
 * {@link Executor}: how much work it can take right now, a way to hand over a job that never waits,
 * and a way to learn when capacity frees up.
 *
 * <p>An executor that does not limit how much work it takes needs none of them, which is why they
 * all come with a default.
 */
@FunctionalInterface
interface JobExecutor extends Executor {

  /**
   * @return how many jobs the executor can take right now, or {@link Integer#MAX_VALUE} if it does
   *     not limit how many jobs it takes at a time
   */
  default int freeCapacity() {
    return Integer.MAX_VALUE;
  }

  /**
   * Hands a job over, refusing it right away when there is no capacity for it instead of waiting
   * for capacity to free up.
   *
   * @throws RejectedExecutionException if the executor has no capacity for the job right now
   */
  default void executeWithoutWaiting(final Runnable command) {
    execute(command);
  }

  /**
   * Registers a callback the executor runs right after it frees a unit of capacity, so a worker can
   * poll again as soon as, and no sooner than, a slot actually reopens. Reading {@link
   * #freeCapacity()} from the callback is guaranteed to see the freed slot.
   *
   * <p>An executor that does not limit how much work it takes never frees capacity in this sense
   * and so never runs the callback, which is why the default does nothing.
   *
   * @param listener the callback to run when capacity frees up
   */
  default void onCapacityAvailable(final Runnable listener) {}
}
