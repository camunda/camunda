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

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;

/**
 * Actor runner metrics
 *
 */
public class ActorRunnerMetrics
{
    public static final String ENABLE_RECORD_JOB_EXECUTION_TIME = "io.zeebe.scheduler.enableRecordJobExecutionTime";
    public static final boolean SHOULD_RECORD_JOB_EXECUTION_TIME = Boolean.getBoolean(ENABLE_RECORD_JOB_EXECUTION_TIME);

    private final CountersManager countersManager;
    private final AtomicCounter jobExecutionTime;
    private final AtomicCounter runnerIdleTime;
    private final AtomicCounter runnerBusyTime;
    private final AtomicCounter jobExecutionCount;
    private final AtomicCounter taskExecutionCount;
    private final AtomicCounter taskStealCount;


    public ActorRunnerMetrics(String runnerName, CountersManager countersManager)
    {
        this.countersManager = countersManager;
        jobExecutionTime = countersManager.newCounter(String.format("%s.jobExecutionTime", runnerName));
        runnerIdleTime = countersManager.newCounter(String.format("%s.runnerIdleTime", runnerName));
        runnerBusyTime = countersManager.newCounter(String.format("%s.runnerBusyTime", runnerName));
        jobExecutionCount = countersManager.newCounter(String.format("%s.jobCount", runnerName));
        taskExecutionCount = countersManager.newCounter(String.format("%s.taskExecutionCount", runnerName));
        taskStealCount = countersManager.newCounter(String.format("%s.taskStealCount", runnerName));
    }

    public void recordJobExecutionTime(long time)
    {
        jobExecutionTime.getAndAddOrdered(time);
    }

    public void incrementTaskStealCount()
    {
        taskStealCount.incrementOrdered();
    }

    public void incrementTaskExecutionCount()
    {
        taskExecutionCount.incrementOrdered();
    }

    public void incrementJobCount()
    {
        jobExecutionCount.incrementOrdered();
    }

    public void recordRunnerIdleTime(long time)
    {
        runnerIdleTime.getAndAddOrdered(time);
    }

    public void recordRunnerBusyTime(long time)
    {
        runnerBusyTime.getAndAddOrdered(time);
    }

    public void close()
    {
        jobExecutionTime.close();
        jobExecutionCount.close();
        taskExecutionCount.close();
        taskStealCount.close();
        runnerIdleTime.close();
        runnerBusyTime.close();
    }

    public void dump(PrintStream ps)
    {
        printCounter(ps, taskExecutionCount);
        printCounter(ps, jobExecutionCount);
        printCounter(ps, taskStealCount);
        printTimeCounter(ps, runnerIdleTime);
        printTimeCounter(ps, runnerBusyTime);

        if (SHOULD_RECORD_JOB_EXECUTION_TIME)
        {
            printTimeCounter(ps, jobExecutionTime);
        }
    }

    private void printCounter(PrintStream ps, AtomicCounter counter)
    {
        final String label = countersManager.getCounterLabel(counter.id());
        final long value = counter.get();

        ps.format("%s: %d\n", label, value);

    }

    private void printTimeCounter(PrintStream ps, AtomicCounter counter)
    {
        final String label = countersManager.getCounterLabel(counter.id());
        long value = counter.get();

        final long hours = TimeUnit.NANOSECONDS.toDays(value);
        value -= TimeUnit.HOURS.toNanos(hours);

        final long minutes = TimeUnit.NANOSECONDS.toMinutes(value);
        value -= TimeUnit.MINUTES.toNanos(minutes);

        final long seconds = TimeUnit.NANOSECONDS.toSeconds(value);
        value -= TimeUnit.SECONDS.toNanos(seconds);

        final long millis = TimeUnit.NANOSECONDS.toMillis(value);
        value -= TimeUnit.MILLISECONDS.toNanos(millis);

        final long micros = TimeUnit.NANOSECONDS.toMicros(value);

        ps.format("%s:\t %dh %dm %02ds %03dms %03dμs\n", label, hours, minutes, seconds, millis, micros);
    }


}
