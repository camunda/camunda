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
package io.atomix.utils.time;

import static com.google.common.base.MoreObjects.toStringHelper;

/** Logical clock. */
public class LogicalClock implements Clock<LogicalTimestamp> {
  private LogicalTimestamp currentTimestamp;

  public LogicalClock() {
    this(new LogicalTimestamp(0));
  }

  public LogicalClock(final LogicalTimestamp currentTimestamp) {
    this.currentTimestamp = currentTimestamp;
  }

  @Override
  public LogicalTimestamp getTime() {
    return currentTimestamp;
  }

  /**
   * Increments the clock and returns the new timestamp.
   *
   * @return the updated clock time
   */
  public LogicalTimestamp increment() {
    return update(new LogicalTimestamp(currentTimestamp.value() + 1));
  }

  /**
   * Updates the clock using the given timestamp.
   *
   * @param timestamp the timestamp with which to update the clock
   * @return the updated clock time
   */
  public LogicalTimestamp update(final LogicalTimestamp timestamp) {
    if (timestamp.value() > currentTimestamp.value()) {
      this.currentTimestamp = timestamp;
    }
    return currentTimestamp;
  }

  /**
   * Increments the clock and updates it using the given timestamp.
   *
   * @param timestamp the timestamp with which to update the clock
   * @return the updated clock time
   */
  public LogicalTimestamp incrementAndUpdate(final LogicalTimestamp timestamp) {
    final long nextValue = currentTimestamp.value() + 1;
    if (timestamp.value() > nextValue) {
      return update(timestamp);
    }
    return increment();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("time", getTime()).toString();
  }
}
