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
package io.camunda.client.impl.backpressure;

/** Runs a task after a delay, without blocking a thread while waiting. */
public interface BackpressureScheduler {

  /**
   * Runs the task once after the given delay.
   *
   * @return a handle to cancel the task if it did not run yet
   */
  Cancellable schedule(Runnable task, long delayMs);

  /** Handle on a scheduled task. */
  interface Cancellable {
    void cancel();
  }
}
