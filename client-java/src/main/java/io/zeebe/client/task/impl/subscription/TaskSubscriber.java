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

import java.util.concurrent.Future;

import org.slf4j.Logger;

import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.TasksClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.impl.CreateTaskSubscriptionCommandImpl;

public class TaskSubscriber extends EventSubscriber
{
    protected static final Logger LOGGER = Loggers.TASK_SUBSCRIPTION_LOGGER;

    protected final TasksClientImpl taskClient;
    protected final TaskSubscriptionSpec subscription;

    protected MsgPackMapper msgPackMapper;

    public TaskSubscriber(
            TasksClientImpl client,
            TaskSubscriptionSpec subscription,
            int partition,
            MsgPackMapper msgPackMapper,
            EventAcquisition acqusition)
    {
        super(partition, subscription.getCapacity(), acqusition);
        this.taskClient = client;
        this.subscription = subscription;
        this.msgPackMapper = msgPackMapper;
    }

    public int pollEvents(TaskHandler taskHandler)
    {
        int polledEvents = pollEvents((e) ->
        {
            final TaskEventImpl taskEvent = msgPackMapper.convert(e.getAsMsgPack(), TaskEventImpl.class);
            taskEvent.updateMetadata(e.getMetadata());

            try
            {
                taskHandler.handle(taskClient, taskEvent);
            }
            catch (Exception handlingException)
            {
                LOGGER.info("An error occurred when handling task " + taskEvent.getMetadata().getKey() +
                        ". Reporting failure to broker.", handlingException);
                try
                {
                    taskClient.fail(taskEvent)
                        .retries(taskEvent.getRetries() - 1)
                        .execute();
                }
                catch (Exception failureException)
                {
                    LOGGER.info("Could not report failure of task " + taskEvent.getMetadata().getKey() +
                        " to broker. Continuing with next task", failureException);
                }
            }
        });

        return polledEvents;
    }

    @Override
    protected void requestEventSourceReplenishment(int eventsProcessed)
    {
        taskClient.increaseSubscriptionCredits(partitionId)
            .subscriberKey(subscriberKey)
            .credits(eventsProcessed)
            .execute();
    }

    @Override
    public Future<? extends EventSubscriptionCreationResult> requestNewSubscription()
    {
        final CreateTaskSubscriptionCommandImpl cmd;
        if (partitionId >= 0)
        {
            cmd = taskClient.createTaskSubscription(partitionId);
        }
        else
        {
            cmd = taskClient.createTaskSubscription(subscription.getTopic());
        }

        return cmd.taskType(subscription.getTaskType())
                .lockDuration(subscription.getLockTime())
                .lockOwner(subscription.getLockOwner())
                .initialCredits(capacity)
                .executeAsync();
    }

    @Override
    public void requestSubscriptionClose()
    {
        taskClient.closeTaskSubscription(partitionId, subscriberKey).execute();
    }

    @Override
    public String toString()
    {
        return "TaskSubscriber[topic=" + subscription.getTopic() + ", partition=" + partitionId +
                ", taskType=" + subscription.getTaskType() + ", subscriberKey=" + subscriberKey + "]";
    }

    @Override
    public String getTopicName()
    {
        return subscription.getTopic();
    }
}
