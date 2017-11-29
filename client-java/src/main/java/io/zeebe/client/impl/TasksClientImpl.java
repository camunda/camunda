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
package io.zeebe.client.impl;

import io.zeebe.client.TasksClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.client.task.cmd.CompleteTaskCommand;
import io.zeebe.client.task.cmd.CreateTaskCommand;
import io.zeebe.client.task.cmd.FailTaskCommand;
import io.zeebe.client.task.cmd.UpdateTaskRetriesCommand;
import io.zeebe.client.task.impl.CloseTaskSubscriptionCommandImpl;
import io.zeebe.client.task.impl.CompleteTaskCommandImpl;
import io.zeebe.client.task.impl.CreateTaskCommandImpl;
import io.zeebe.client.task.impl.CreateTaskSubscriptionCommandImpl;
import io.zeebe.client.task.impl.FailTaskCommandImpl;
import io.zeebe.client.task.impl.IncreaseTaskSubscriptionCreditsCmdImpl;
import io.zeebe.client.task.impl.UpdateRetriesCommandImpl;

public class TasksClientImpl implements TasksClient
{
    protected final ZeebeClientImpl client;

    public TasksClientImpl(final ZeebeClientImpl client)
    {
        this.client = client;
    }

    @Override
    public CreateTaskCommand create(String topic, String type)
    {
        return new CreateTaskCommandImpl(client.getCommandManager(), client.getMsgPackConverter(), topic, type);
    }

    @Override
    public FailTaskCommand fail(TaskEvent event)
    {
        return new FailTaskCommandImpl(client.getCommandManager(), event);
    }

    @Override
    public UpdateTaskRetriesCommand updateRetries(TaskEvent event)
    {
        return new UpdateRetriesCommandImpl(client.getCommandManager(), event);
    }

    @Override
    public CompleteTaskCommand complete(TaskEvent baseEvent)
    {
        return new CompleteTaskCommandImpl(client.getCommandManager(), (TaskEventImpl) baseEvent);
    }

    @Override
    public TaskSubscriptionBuilder newTaskSubscription(String topic)
    {
        return client.getSubscriptionManager().newTaskSubscription(client, topic);
    }

    @Override
    public PollableTaskSubscriptionBuilder newPollableTaskSubscription(String topic)
    {
        return client.getSubscriptionManager().newPollableTaskSubscription(client, topic);
    }

    public CreateTaskSubscriptionCommandImpl createTaskSubscription(int partitionId)
    {
        return new CreateTaskSubscriptionCommandImpl(client.getCommandManager(), partitionId);
    }

    public CreateTaskSubscriptionCommandImpl createTaskSubscription(String topic)
    {
        return new CreateTaskSubscriptionCommandImpl(client.getCommandManager(), topic);
    }

    public CloseTaskSubscriptionCommandImpl closeTaskSubscription(int partitionId, long subscriberKey)
    {
        return new CloseTaskSubscriptionCommandImpl(client.getCommandManager(), partitionId, subscriberKey);
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl increaseSubscriptionCredits(int partitionId)
    {
        return new IncreaseTaskSubscriptionCreditsCmdImpl(client.getCommandManager(), partitionId);
    }

}
