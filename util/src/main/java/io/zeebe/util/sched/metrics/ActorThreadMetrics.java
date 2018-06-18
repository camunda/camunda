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

/** Actor runner metrics */
public class ActorThreadMetrics implements AutoCloseable {
  private final Metric threadIdleTime;
  private final Metric threadBusyTime;
  private final Metric jobExecutionCount;
  private final Metric taskStealCount;
  private final Metric taskExecutionCount;

  public ActorThreadMetrics(String threadName, MetricsManager metricsManager) {
    threadIdleTime =
        metricsManager
            .newMetric("scheduler_thread_runtime_ns")
            .type("counter")
            .label("thread", threadName)
            .label("mode", "idle")
            .create();

    threadBusyTime =
        metricsManager
            .newMetric("scheduler_thread_runtime_ns")
            .type("counter")
            .label("thread", threadName)
            .label("mode", "busy")
            .create();

    jobExecutionCount =
        metricsManager
            .newMetric("scheduler_thread_job_count")
            .type("counter")
            .label("thread", threadName)
            .create();

    taskStealCount =
        metricsManager
            .newMetric("scheduler_thread_task_count")
            .type("counter")
            .label("thread", threadName)
            .label("type", "steal")
            .create();

    taskExecutionCount =
        metricsManager
            .newMetric("scheduler_thread_task_count")
            .type("counter")
            .label("thread", threadName)
            .label("type", "run")
            .create();
  }

  public void incrementTaskStealCount() {
    taskStealCount.incrementOrdered();
  }

  public void incrementTaskExecutionCount() {
    taskExecutionCount.incrementOrdered();
  }

  public void incrementJobCount() {
    jobExecutionCount.incrementOrdered();
  }

  public void recordRunnerIdleTime(long time) {
    threadIdleTime.getAndAddOrdered(time);
  }

  public void recordRunnerBusyTime(long time) {
    threadBusyTime.getAndAddOrdered(time);
  }

  @Override
  public void close() {
    jobExecutionCount.close();
    taskStealCount.close();
    threadIdleTime.close();
    threadBusyTime.close();
    taskExecutionCount.close();
  }
}
