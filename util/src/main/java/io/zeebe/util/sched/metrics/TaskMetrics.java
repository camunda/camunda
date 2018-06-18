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
package io.zeebe.util.sched.metrics;

import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;

public class TaskMetrics implements AutoCloseable {
  private final Metric executionCount;
  private final Metric totalExecutionTime;
  private final Metric maxExecutionTime;

  public TaskMetrics(String taskName, MetricsManager metricsManager) {
    executionCount =
        metricsManager
            .newMetric("scheduler_task_execution_count")
            .type("counter")
            .label("task", taskName)
            .create();

    totalExecutionTime =
        metricsManager
            .newMetric("scheduler_task_execution_time_total")
            .type("counter")
            .label("task", taskName)
            .create();

    maxExecutionTime =
        metricsManager
            .newMetric("scheduler_task_execution_time_max")
            .type("gauge")
            .label("task", taskName)
            .create();
  }

  public void reportExecutionTime(long executionTimeNs) {
    assert executionTimeNs >= 0;

    final long max = maxExecutionTime.getWeak();
    if (executionTimeNs > max) {
      maxExecutionTime.setOrdered(executionTimeNs);
    }

    totalExecutionTime.getAndAddOrdered(executionTimeNs);

    executionCount.incrementOrdered();
  }

  @Override
  public void close() {
    executionCount.close();
    totalExecutionTime.close();
    maxExecutionTime.close();
  }
}
