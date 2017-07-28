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
package io.zeebe.client.task.impl;

import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.Partition;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class CreateTaskSubscriptionCommandImpl extends ControlMessageRequest<TaskSubscription>
{
    protected TaskSubscription subscription;

    public CreateTaskSubscriptionCommandImpl(RequestManager client, Partition target)
    {
        super(client, ControlMessageType.ADD_TASK_SUBSCRIPTION, target, TaskSubscription.class);
        this.subscription = new TaskSubscription(target.getTopicName(), target.getPartitionId());
    }

    public CreateTaskSubscriptionCommandImpl lockOwner(final String lockOwner)
    {
        this.subscription.setLockOwner(lockOwner);
        return this;
    }

    public CreateTaskSubscriptionCommandImpl initialCredits(final int initialCredits)
    {
        this.subscription.setCredits(initialCredits);
        return this;
    }

    public CreateTaskSubscriptionCommandImpl lockDuration(final long lockDuration)
    {
        this.subscription.setLockDuration(lockDuration);
        return this;
    }

    public CreateTaskSubscriptionCommandImpl taskType(final String taskType)
    {
        this.subscription.setTaskType(taskType);
        return this;
    }

    @Override
    public Object getRequest()
    {
        return subscription;
    }

}
