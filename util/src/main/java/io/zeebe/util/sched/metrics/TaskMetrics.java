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

import static io.zeebe.util.sched.metrics.SchedulerMetrics.TYPE_TEMPORAL_VALUE;

import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;

public class TaskMetrics implements AutoCloseable
{
    private final AtomicCounter executionCount;
    private final AtomicCounter totalExecutionTime;
    private final AtomicCounter maxExecutionTime;

    public TaskMetrics(String taskName, CountersManager countersManager)
    {
        executionCount = countersManager.newCounter(String.format("%s.taskExecutionCount", taskName));
        totalExecutionTime = countersManager.newCounter(String.format("%s.taskTotalExecutionTime", taskName), TYPE_TEMPORAL_VALUE);
        maxExecutionTime = countersManager.newCounter(String.format("%s.taskMaxExecutionTime", taskName), TYPE_TEMPORAL_VALUE);
    }

    public void reportExecutionTime(long executionTimeNs)
    {
        assert executionTimeNs >= 0;

        final long max = maxExecutionTime.getWeak();
        if (executionTimeNs > max)
        {
            maxExecutionTime.setOrdered(executionTimeNs);
        }

        totalExecutionTime.getAndAddOrdered(executionTimeNs);

        executionCount.incrementOrdered();
    }

    @Override
    public void close()
    {
        executionCount.close();
        totalExecutionTime.close();
        maxExecutionTime.close();
    }

}
