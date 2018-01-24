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

import static org.agrona.UnsafeAccess.UNSAFE;

@SuppressWarnings("restriction")
public class ActorConditionImpl implements ActorCondition, ActorSubscription
{
    private static final long IS_TRUE_OFFSET;

    private volatile int isTrue;

    private final ActorJob job;
    private final String conditionName;
    private final ActorTask task;

    static
    {
        try
        {
            IS_TRUE_OFFSET = UNSAFE.objectFieldOffset(ActorConditionImpl.class.getDeclaredField("isTrue"));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public ActorConditionImpl(String conditionName, ActorJob job)
    {
        this.conditionName = conditionName;
        this.job = job;
        this.task = job.task;
    }


    public void trigger()
    {
        if (UNSAFE.compareAndSwapInt(this, IS_TRUE_OFFSET, 0, 1))
        {
            if (task.tryWakeup())
            {
                final ActorTaskRunner taskRunner = ActorTaskRunner.current();
                if (taskRunner != null)
                {
                    taskRunner.submit(task);
                }
                else
                {
                    task.getScheduler().reSubmitActor(task);
                }
            }
        }
    }


    @Override
    public void signal()
    {
        final ActorTaskRunner runner = ActorTaskRunner.current();
        if (runner != null)
        {
            // delay triggering of conditions
            final ActorJob job = ActorTaskRunner.current().getCurrentJob();
            job.addTriggeredCondition(this);
        }
        else
        {
            trigger();
        }
    }

    @Override
    public void onJobCompleted()
    {
        UNSAFE.compareAndSwapInt(this, IS_TRUE_OFFSET, 1, 0);
    }

    @Override
    public boolean poll()
    {
        return isTrue == 1;
    }

    @Override
    public ActorJob getJob()
    {
        return job;
    }

    @Override
    public boolean isRecurring()
    {
        return true;
    }

    @Override
    public String toString()
    {
        return "Condition " + conditionName + ": " + isTrue;
    }
}
