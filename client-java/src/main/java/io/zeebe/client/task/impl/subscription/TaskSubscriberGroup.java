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

import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscription;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

public class TaskSubscriberGroup extends SubscriberGroup<TaskSubscriber> implements
    TaskSubscription, PollableTaskSubscription
{

    protected final TaskSubscriptionSpec subscription;

    public TaskSubscriberGroup(
            ActorControl actor,
            ZeebeClientImpl client,
            SubscriptionManager acquisition,
            TaskSubscriptionSpec subscription)
    {
        super(actor, client, acquisition, subscription.getTopic());
        this.subscription = subscription;
    }

    @Override
    public int poll()
    {
        return poll(subscription.getTaskHandler());
    }

    @Override
    public int poll(TaskHandler taskHandler)
    {
        int workCount = 0;
        for (TaskSubscriber subscriber : subscribersList)
        {
            workCount += subscriber.pollEvents(taskHandler);
        }

        return workCount;
    }

    @Override
    public boolean isManagedGroup()
    {
        return subscription.isManaged();
    }

    @Override
    protected ActorFuture<? extends EventSubscriptionCreationResult> requestNewSubscriber(int partitionId)
    {
        return client.tasks().createTaskSubscription(partitionId)
                .taskType(subscription.getTaskType())
                .lockDuration(subscription.getLockTime())
                .lockOwner(subscription.getLockOwner())
                .initialCredits(subscription.getCapacity())
                .executeAsync();
    }

    @Override
    protected TaskSubscriber buildSubscriber(EventSubscriptionCreationResult result)
    {
        return new TaskSubscriber(
                client.tasks(),
                subscription,
                result.getSubscriberKey(),
                result.getEventPublisher(),
                result.getPartitionId(),
                this,
                client.getMsgPackMapper(),
                subscriptionManager);
    }

    @Override
    protected String describeGroup()
    {
        return subscription.toString();
    }

}
