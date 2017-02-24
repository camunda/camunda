package org.camunda.tngp.client.task.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.EventSubscription;
import org.camunda.tngp.client.impl.Loggers;
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
    protected final AsyncTasksClient taskClient;

    protected final String taskType;
    protected final int topicId;
    protected final long lockTime;
    protected final int lockOwner;
    protected final AtomicInteger remainingCapacity = new AtomicInteger(0);

    protected boolean autoComplete;
    protected MsgPackMapper msgPackMapper;

    public TaskSubscriptionImpl(
            AsyncTasksClient taskClient,
            TaskHandler taskHandler,
            String taskType,
            int taskQueueId,
            long lockTime,
            int lockOwner,
            int upperBoundCapacity,
            EventAcquisition<TaskSubscriptionImpl> acquisition,
            MsgPackMapper msgPackMapper,
            boolean autoComplete)
    {
        super(acquisition, upperBoundCapacity);
        this.taskClient = taskClient;
        this.taskHandler = taskHandler;
        this.taskType = taskType;
        this.topicId = taskQueueId;
        this.lockTime = lockTime;
        this.lockOwner = lockOwner;
        this.autoComplete = autoComplete;
        this.msgPackMapper = msgPackMapper;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public int getTopicId()
    {
        return topicId;
    }

    public long getLockTime()
    {
        return lockTime;
    }

    public int getLockOwner()
    {
        return lockOwner;
    }


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

            remainingCapacity.incrementAndGet();
        });

        return polledEvents;
    }

    public boolean isManagedSubscription()
    {
        return taskHandler != null;
    }

    public int getAndDecrementRemainingCapacity()
    {
        final int capacity = remainingCapacity.get();

        remainingCapacity.addAndGet(-capacity);

        return capacity;
    }
}
