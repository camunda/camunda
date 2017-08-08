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

import org.slf4j.Logger;

import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.TasksClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscription;

public class TaskSubscriptionImpl
    extends EventSubscription<TaskSubscriptionImpl>
    implements TaskSubscription, PollableTaskSubscription
{
    protected static final Logger LOGGER = Loggers.TASK_SUBSCRIPTION_LOGGER;

    protected final TaskHandler taskHandler;
    protected final TasksClientImpl taskClient;

    protected final String taskType;
    protected final long lockTime;
    protected final String lockOwner;

    protected MsgPackMapper msgPackMapper;

    public TaskSubscriptionImpl(
            TasksClientImpl client,
            String topic,
            int partition,
            TaskHandler taskHandler,
            String taskType,
            long lockTime,
            String lockOwner,
            int capacity,
            MsgPackMapper msgPackMapper,
            EventAcquisition<TaskSubscriptionImpl> acqusition)
    {
        super(topic, partition, capacity, acqusition);
        this.taskClient = client;
        this.taskHandler = taskHandler;
        this.taskType = taskType;
        this.lockTime = lockTime;
        this.lockOwner = lockOwner;
        this.msgPackMapper = msgPackMapper;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public long getLockTime()
    {
        return lockTime;
    }

    public String getLockOwner()
    {
        return lockOwner;
    }


    @Override
    public int poll()
    {
        return poll(taskHandler);
    }

    @Override
    public int poll(TaskHandler taskHandler)
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
                LOGGER.info("An error ocurred when handling task " + taskEvent.getMetadata().getKey() +
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
    public boolean isManagedSubscription()
    {
        return taskHandler != null;
    }

    @Override
    protected void requestEventSourceReplenishment(int eventsProcessed)
    {
        taskClient.increaseSubscriptionCredits(topic, partitionId)
            .subscriberKey(subscriberKey)
            .credits(eventsProcessed)
            .execute();
    }

    @Override
    public EventSubscriptionCreationResult requestNewSubscription()
    {
        return taskClient.createTaskSubscription(topic, partitionId)
                .taskType(taskType)
                .lockDuration(lockTime)
                .lockOwner(lockOwner)
                .initialCredits(capacity)
                .execute();
    }

    @Override
    public void requestSubscriptionClose()
    {
        taskClient.closeTaskSubscription(topic, partitionId, subscriberKey).execute();
    }

    @Override
    public String toString()
    {
        return "TaskSubscriptionImpl [taskType=" + taskType + ", subscriberKey=" + subscriberKey + "]";
    }
}
