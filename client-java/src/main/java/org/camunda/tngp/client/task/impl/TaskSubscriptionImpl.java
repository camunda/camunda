package org.camunda.tngp.client.task.impl;

import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.EventSubscription;
import org.camunda.tngp.client.impl.Loggers;
import org.camunda.tngp.client.impl.TaskTopicClientImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.impl.data.MsgPackMapper;
import org.camunda.tngp.client.task.PollableTaskSubscription;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscription;
import org.slf4j.Logger;

public class TaskSubscriptionImpl
    extends EventSubscription<TaskSubscriptionImpl>
    implements TaskSubscription, PollableTaskSubscription
{
    protected static final Logger LOGGER = Loggers.TASK_SUBSCRIPTION_LOGGER;

    protected final TaskHandler taskHandler;
    protected final TaskTopicClientImpl taskClient;

    protected final String taskType;
    protected final long lockTime;
    protected final int lockOwner;

    protected boolean autoComplete;
    protected MsgPackMapper msgPackMapper;

    public TaskSubscriptionImpl(
            TaskTopicClientImpl client,
            TaskHandler taskHandler,
            String taskType,
            long lockTime,
            int lockOwner,
            int capacity,
            EventAcquisition<TaskSubscriptionImpl> acquisition,
            MsgPackMapper msgPackMapper,
            boolean autoComplete)
    {
        super(acquisition, capacity);
        this.taskClient = client;
        this.taskHandler = taskHandler;
        this.taskType = taskType;
        this.lockTime = lockTime;
        this.lockOwner = lockOwner;
        this.autoComplete = autoComplete;
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

    public int getLockOwner()
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
            final TaskEvent taskEvent = msgPackMapper.convert(e.getAsMsgPack(), TaskEvent.class);
            final TaskImpl task = new TaskImpl(taskClient, this, e.getEventKey(), taskEvent);

            try
            {
                taskHandler.handle(task);

                if (autoComplete && !task.isCompleted())
                {
                    task.complete();
                }
            }
            catch (Exception ex)
            {
                LOGGER.info("An error ocurred when handling task " + task.getKey() + ". Reporting to broker.", ex);
                task.fail(ex);
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
        taskClient.increaseSubscriptionCredits()
            .subscriptionId(subscriberKey)
            .credits(eventsProcessed)
            .execute();
    }

    @Override
    public EventSubscriptionCreationResult requestNewSubscription()
    {
        return taskClient.brokerTaskSubscription()
                .taskType(taskType)
                .lockDuration(lockTime)
                .lockOwner(lockOwner)
                .initialCredits(capacity)
                .execute();
    }

    @Override
    public void requestSubscriptionClose()
    {
        taskClient.closeBrokerTaskSubscription()
            .subscriptionId(subscriberKey)
            .execute();

    }

    @Override
    public String getTopicName()
    {
        return taskClient.getTopicName();
    }

    @Override
    public int getPartitionId()
    {
        return taskClient.getPartitionId();
    }
}
