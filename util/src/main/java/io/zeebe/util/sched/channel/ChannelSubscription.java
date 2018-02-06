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
package io.zeebe.util.sched.channel;

import io.zeebe.util.sched.*;

public class ChannelSubscription implements ActorSubscription
{
    final ActorJob subscriptionJob;
    final ConsumableChannel channel;

    public ChannelSubscription(ActorJob subscriptionJob, ConsumableChannel channel)
    {
        this.subscriptionJob = subscriptionJob;
        this.channel = channel;
    }

    public void signalReadAvailable()
    {
        final ActorTask task = subscriptionJob.getTask();

        if (task.tryWakeup())
        {
            final ActorTaskRunner current = ActorTaskRunner.current();

            if (current != null)
            {
                current.submit(task);
            }
            else
            {
                // make it possible for non-actor runner threads to signal consumers
                task.getScheduler().reSubmitActor(task);
            }
        }
    }

    @Override
    public boolean isRecurring()
    {
        return true;
    }

    public ConsumableChannel getChannel()
    {
        return channel;
    }

    @Override
    public boolean poll()
    {
        return channel.hasAvailable();
    }

    @Override
    public ActorJob getJob()
    {
        return subscriptionJob;
    }
}
