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
package io.zeebe.client.task.impl.subscription;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;

public class PollableTaskSubscriptionBuilderImpl implements PollableTaskSubscriptionBuilder
{
    protected final TaskSubscriberGroupBuilder subscriberBuilder;

    public PollableTaskSubscriptionBuilderImpl(
            ZeebeClient client,
            String topic,
            SubscriptionManager taskAcquisition)
    {
        this.subscriberBuilder = new TaskSubscriberGroupBuilder(client, topic, taskAcquisition);
    }

    @Override
    public PollableTaskSubscriptionBuilder taskType(String taskType)
    {
        subscriberBuilder.taskType(taskType);
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilder lockTime(long lockDuration)
    {
        subscriberBuilder.lockTime(lockDuration);
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilder lockTime(Duration lockDuration)
    {
        return lockTime(lockDuration.toMillis());
    }

    @Override
    public PollableTaskSubscriptionBuilder lockOwner(String lockOwner)
    {
        subscriberBuilder.lockOwner(lockOwner);
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilderImpl taskFetchSize(int numTasks)
    {
        subscriberBuilder.taskFetchSize(numTasks);
        return this;
    }

    @Override
    public PollableTaskSubscription open()
    {
        final Future<TaskSubscriberGroup> subscriberGroup = subscriberBuilder.build();

        try
        {
            return subscriberGroup.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new ClientException("Could not open subscription", e);
        }
    }
}
