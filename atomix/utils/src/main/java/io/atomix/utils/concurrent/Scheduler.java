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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Scheduler. */
public interface Scheduler {

  /**
   * Schedules a runnable after a delay.
   *
   * @param delay the delay after which to run the callback
   * @param timeUnit the time unit
   * @param callback the callback to run
   * @return the scheduled callback
   */
  default Scheduled schedule(final long delay, final TimeUnit timeUnit, final Runnable callback) {
    return schedule(Duration.ofMillis(timeUnit.toMillis(delay)), callback);
  }

  /**
   * Schedules a runnable after a delay.
   *
   * @param delay the delay after which to run the callback
   * @param callback the callback to run
   * @return the scheduled callback
   */
  Scheduled schedule(Duration delay, Runnable callback);

  /**
   * Schedules a runnable at a fixed rate.
   *
   * @param initialDelay the initial delay
   * @param interval the interval at which to run the callback
   * @param timeUnit the time unit
   * @param callback the callback to run
   * @return the scheduled callback
   */
  default Scheduled schedule(
      final long initialDelay,
      final long interval,
      final TimeUnit timeUnit,
      final Runnable callback) {
    return schedule(
        Duration.ofMillis(timeUnit.toMillis(initialDelay)),
        Duration.ofMillis(timeUnit.toMillis(interval)),
        callback);
  }

  /**
   * Schedules a runnable at a fixed rate.
   *
   * @param initialDelay the initial delay
   * @param interval the interval at which to run the callback
   * @param callback the callback to run
   * @return the scheduled callback
   */
  Scheduled schedule(Duration initialDelay, Duration interval, Runnable callback);
}
