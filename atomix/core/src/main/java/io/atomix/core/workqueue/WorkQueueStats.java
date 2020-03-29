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

/** Statistics for a {@link AsyncWorkQueue}. */
public final class WorkQueueStats {

  private long totalPending;
  private long totalInProgress;
  private long totalCompleted;

  private WorkQueueStats() {}

  /**
   * Returns a {@code WorkQueueStats} builder.
   *
   * @return builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the total pending tasks. These are the tasks that are added but not yet picked up.
   *
   * @return total pending tasks.
   */
  public long totalPending() {
    return this.totalPending;
  }

  /**
   * Returns the total in progress tasks. These are the tasks that are currently being worked on.
   *
   * @return total in progress tasks.
   */
  public long totalInProgress() {
    return this.totalInProgress;
  }

  /**
   * Returns the total completed tasks.
   *
   * @return total completed tasks.
   */
  public long totalCompleted() {
    return this.totalCompleted;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("totalPending", totalPending)
        .add("totalInProgress", totalInProgress)
        .add("totalCompleted", totalCompleted)
        .toString();
  }

  public static class Builder {

    WorkQueueStats workQueueStats = new WorkQueueStats();

    public Builder withTotalPending(final long value) {
      workQueueStats.totalPending = value;
      return this;
    }

    public Builder withTotalInProgress(final long value) {
      workQueueStats.totalInProgress = value;
      return this;
    }

    public Builder withTotalCompleted(final long value) {
      workQueueStats.totalCompleted = value;
      return this;
    }

    public WorkQueueStats build() {
      return workQueueStats;
    }
  }
}
