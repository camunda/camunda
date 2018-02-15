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

public class ActorFutureSubscription implements ActorSubscription
{

    private volatile boolean completed = false;
    private final ActorTask task;
    private final ActorJob callbackJob;

    public ActorFutureSubscription(ActorTask task, ActorJob callbackJob)
    {
        this.task = task;
        this.callbackJob = callbackJob;
    }

    @Override
    public boolean poll()
    {
        return completed;
    }

    @Override
    public ActorJob getJob()
    {
        return callbackJob;
    }

    @Override
    public boolean isRecurring()
    {
        return false;
    }

    public void trigger()
    {
        completed = true;

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
