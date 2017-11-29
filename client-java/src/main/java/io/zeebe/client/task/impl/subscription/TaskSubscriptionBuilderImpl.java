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

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscription;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.util.EnsureUtil;

public class TaskSubscriptionBuilderImpl implements TaskSubscriptionBuilder
{
    protected TaskHandler taskHandler;

    protected final TaskSubscriberGroupBuilder subscriberBuilder;

    public TaskSubscriptionBuilderImpl(
            ZeebeClient client,
            ClientTopologyManager topologyManager,
            String topic,
            EventAcquisition taskAcquisition,
            MsgPackMapper msgPackMapper)
    {
        this.subscriberBuilder = new TaskSubscriberGroupBuilder(client, topologyManager, topic, taskAcquisition, msgPackMapper);
    }

    @Override
    public TaskSubscriptionBuilder taskType(String taskType)
    {
        subscriberBuilder.taskType(taskType);
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockTime(long lockDuration)
    {
        subscriberBuilder.lockTime(lockDuration);
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockTime(Duration lockDuration)
    {
        return lockTime(lockDuration.toMillis());
    }

    @Override
    public TaskSubscriptionBuilder handler(TaskHandler handler)
    {
        this.taskHandler = handler;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder taskFetchSize(int numTasks)
    {
        subscriberBuilder.taskFetchSize(numTasks);
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockOwner(String lockOwner)
    {
        subscriberBuilder.lockOwner(lockOwner);
        return this;
    }

    @Override
    public TaskSubscription open()
    {
        EnsureUtil.ensureNotNull("taskHandler", taskHandler);
        subscriberBuilder.taskHandler(taskHandler);

        final TaskSubscriberGroup subscriberGroup = subscriberBuilder.build();
        subscriberGroup.open();

        return subscriberGroup;
    }

}
