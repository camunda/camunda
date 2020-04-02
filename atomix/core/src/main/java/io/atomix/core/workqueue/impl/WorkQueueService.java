/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.core.workqueue.impl;

import io.atomix.core.workqueue.Task;
import io.atomix.core.workqueue.WorkQueueStats;
import io.atomix.primitive.operation.Command;
import io.atomix.primitive.operation.Query;
import java.util.Collection;

/** Work queue service. */
public interface WorkQueueService {

  /**
   * Adds a collection of tasks to the work queue.
   *
   * @param items collection of task items
   */
  @Command
  void add(Collection<byte[]> items);

  /**
   * Picks up multiple tasks from the work queue to work on.
   *
   * @param maxItems maximum number of items to take from the queue. The actual number of tasks
   *     returned can be at the max this number
   * @return an empty collection if there are no unassigned tasks in the work queue
   */
  @Command
  Collection<Task<byte[]>> take(int maxItems);

  /**
   * Completes a collection of tasks.
   *
   * @param taskIds ids of tasks to complete
   */
  @Command
  void complete(Collection<String> taskIds);

  /**
   * Returns work queue statistics.
   *
   * @return work queue stats
   */
  @Query
  WorkQueueStats stats();

  /** Registers the current session as a task processor. */
  @Command
  void register();

  /** Unregisters the current session as a task processor. */
  @Command
  void unregister();

  /** Removes all pending tasks from the queue. */
  @Command
  void clear();
}
