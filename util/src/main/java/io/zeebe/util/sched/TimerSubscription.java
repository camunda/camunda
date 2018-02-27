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
package io.zeebe.util.sched;

import java.util.concurrent.TimeUnit;

public class TimerSubscription implements ActorSubscription, ScheduledTimer
{
    private volatile boolean isDone = false;
    private volatile boolean isCanceled = false;

    private final ActorJob job;
    private final TimeUnit timeUnit;
    private final long deadline;
    private final boolean isRecurring;

    private long timerId = -1L;

    public TimerSubscription(ActorJob job, long deadline, TimeUnit timeUnit, boolean isRecurring)
    {
        this.job = job;
        this.timeUnit = timeUnit;
        this.deadline = deadline;
        this.isRecurring = isRecurring;
    }

    @Override
    public boolean poll()
    {
        return isDone;
    }

    @Override
    public ActorJob getJob()
    {
        return job;
    }

    @Override
    public boolean isRecurring()
    {
        return isRecurring;
    }

    @Override
    public void onJobCompleted()
    {
        isDone = false;

        if (isRecurring && !isCanceled)
        {
            submit();
        }
    }

    public void setTimerId(long timerId)
    {
        this.timerId = timerId;
    }

    public long getTimerId()
    {
        return timerId;
    }

    @Override
    public void cancel()
    {
        if (!isCanceled && !isDone)
        {
            isCanceled = true;

            ActorTaskRunner.current().removeTimer(this);
        }
    }

    public void submit()
    {
        final ActorTaskRunner runner = ActorTaskRunner.current();
        runner.scheduleTimer(this);
    }

    public long getDeadline()
    {
        return deadline;
    }

    public TimeUnit getTimeUnit()
    {
        return timeUnit;
    }

    public void onTimerExpired(TimeUnit timeUnit, long now)
    {
        if (!isCanceled)
        {
            isDone = true;

            final ActorTask task = job.task;

            if (task.tryWakeup())
            {
                ActorTaskRunner.current().submit(task);
            }
        }
    }
}
