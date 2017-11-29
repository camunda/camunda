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

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.TasksClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscription;

public class TaskSubscriberGroup extends EventSubscriberGroup<TaskSubscriber> implements
    TaskSubscription, PollableTaskSubscription
{

    protected final TaskSubscriptionSpec subscription;
    protected final MsgPackMapper msgPackMapper;

    public TaskSubscriberGroup(
            ZeebeClient client,
            EventAcquisition acquisition,
            TaskSubscriptionSpec subscription,
            MsgPackMapper msgPackMapper)
    {
        super(acquisition, client, subscription.getTopic());
        this.subscription = subscription;
        this.msgPackMapper = msgPackMapper;
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
        for (TaskSubscriber subscriber : subscribers)
        {
            workCount += subscriber.pollEvents(taskHandler);
        }

        return workCount;
    }

    @Override
    protected TaskSubscriber buildSubscriber(int partition)
    {
        return new TaskSubscriber((TasksClientImpl) client.tasks(), subscription, partition, msgPackMapper, acquisition);
    }

    @Override
    public boolean isManagedGroup()
    {
        return subscription.isManaged();
    }

    @Override
    protected String describeGroupSpec()
    {
        return subscription.toString();
    }

}
