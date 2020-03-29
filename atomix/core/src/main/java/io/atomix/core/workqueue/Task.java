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

import com.google.common.base.MoreObjects;
import java.util.function.Function;

/**
 * {@link AsyncWorkQueue} task.
 *
 * @param <E> task payload type.
 */
public class Task<E> {
  private final E payload;
  private final String taskId;

  private Task() {
    payload = null;
    taskId = null;
  }

  /**
   * Constructs a new task instance.
   *
   * @param taskId task identifier
   * @param payload task payload
   */
  public Task(final String taskId, final E payload) {
    this.taskId = taskId;
    this.payload = payload;
  }

  /**
   * Returns the task identifier.
   *
   * @return task id
   */
  public String taskId() {
    return taskId;
  }

  /**
   * Returns the task payload.
   *
   * @return task payload
   */
  public E payload() {
    return payload;
  }

  /**
   * Maps task from one payload type to another.
   *
   * @param <F> future type
   * @param mapper type mapper.
   * @return mapped task.
   */
  public <F> Task<F> map(final Function<E, F> mapper) {
    return new Task<>(taskId, mapper.apply(payload));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("taskId", taskId)
        .add("payload", payload)
        .toString();
  }
}
