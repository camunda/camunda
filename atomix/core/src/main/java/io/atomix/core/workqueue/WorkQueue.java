/*
 * Copyright 2016-present Open Networking Foundation
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
package io.atomix.core.workqueue;

import com.google.common.collect.ImmutableList;
import io.atomix.primitive.SyncPrimitive;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Distributed Work Queue primitive.
 *
 * <p>Work queue serves as a buffer allowing producers to {@link #addMultiple(Collection) add} tasks
 * and consumers to {@link #take() take} tasks to process.
 *
 * <p>In the system each task is tracked via its unique task identifier which is returned when a
 * task is taken. Work queue guarantees that a task can be taken by only one consumer at a time.
 * Once it finishes processing a consumer must invoke the {@link #complete(Collection) complete}
 * method to mark the task(s) as completed. Tasks thus completed are removed from the queue. If a
 * consumer unexpectedly terminates before it can complete all its tasks are returned back to the
 * queue so that other consumers can pick them up. Since there is a distinct possibility that tasks
 * could be processed more than once (under failure conditions), care should be taken to ensure task
 * processing logic is idempotent.
 *
 * @param <E> task payload type.
 */
public interface WorkQueue<E> extends SyncPrimitive {

  /**
   * Adds a collection of tasks to the work queue.
   *
   * @param items collection of task items
   */
  void addMultiple(Collection<E> items);

  /**
   * Picks up multiple tasks from the work queue to work on.
   *
   * <p>Tasks that are taken remain invisible to other consumers as long as the consumer stays
   * alive. If a consumer unexpectedly terminates before {@link #complete(String...) completing} the
   * task, the task becomes visible again to other consumers to process.
   *
   * @param maxItems maximum number of items to take from the queue. The actual number of tasks
   *     returned can be at the max this number
   * @return an empty collection if there are no unassigned tasks in the work queue
   */
  Collection<Task<E>> take(int maxItems);

  /**
   * Completes a collection of tasks.
   *
   * @param taskIds ids of tasks to complete
   */
  void complete(Collection<String> taskIds);

  /**
   * Registers a task processing callback to be automatically invoked when new tasks are added to
   * the work queue.
   *
   * @param taskProcessor task processing callback
   * @param parallelism max tasks that can be processed in parallel
   * @param executor executor to use for processing the tasks
   */
  void registerTaskProcessor(Consumer<E> taskProcessor, int parallelism, Executor executor);

  /**
   * Stops automatically processing tasks from work queue. This call nullifies the effect of a
   * previous {@link #registerTaskProcessor registerTaskProcessor} call.
   */
  void stopProcessing();

  /**
   * Returns work queue statistics.
   *
   * @return work queue stats
   */
  WorkQueueStats stats();

  /**
   * Completes a collection of tasks.
   *
   * @param taskIds var arg list of task ids
   */
  default void complete(final String... taskIds) {
    complete(Arrays.asList(taskIds));
  }

  /**
   * Adds a single task to the work queue.
   *
   * @param item task item
   */
  default void addOne(final E item) {
    addMultiple(ImmutableList.of(item));
  }

  /**
   * Picks up a single task from the work queue to work on.
   *
   * <p>Tasks that are taken remain invisible to other consumers as long as the consumer stays
   * alive. If a consumer unexpectedly terminates before {@link #complete(String...) completing} the
   * task, the task becomes visible again to other consumers to process.
   *
   * @return future for the task. The future can be completed with null, if there are no unassigned
   *     tasks in the work queue
   */
  default Task<E> take() {
    final Collection<Task<E>> tasks = take(1);
    return tasks.isEmpty() ? null : tasks.iterator().next();
  }

  @Override
  AsyncWorkQueue<E> async();
}
