package org.camunda.tngp.client.task.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.util.EnsureUtil;

public class TaskSubscriptionBuilderImpl implements TaskSubscriptionBuilder
{

    /**
     * MaxTasks is not yet implemented in the broker
     */
    protected static final int MAX_TASKS = 1;

    protected String taskType;
    protected long lockTime = TimeUnit.MINUTES.toMillis(1);
    protected int taskQueueId = 0;
    protected TaskHandler taskHandler;

    protected final TaskAcquisition taskAcquisition;
    protected final boolean autoCompleteTasks;

    public TaskSubscriptionBuilderImpl(TaskAcquisition taskAcquisition, boolean autoCompleteTasks)
    {
        this.taskAcquisition = taskAcquisition;
        this.autoCompleteTasks = autoCompleteTasks;
    }

    @Override
    public TaskSubscriptionBuilder taskType(String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockTime(long lockDuration)
    {
        this.lockTime = lockDuration;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockTime(Duration lockDuration)
    {
        return lockTime(lockDuration.toMillis());
    }

    @Override
    public TaskSubscriptionBuilder taskQueueId(int taskQueueId)
    {
        this.taskQueueId = taskQueueId;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder handler(TaskHandler handler)
    {
        this.taskHandler = handler;
        return this;
    }

    @Override
    public TaskSubscriptionImpl open()
    {
        EnsureUtil.ensureNotNull("taskHandler", taskHandler);
        EnsureUtil.ensureNotNull("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);

        final TaskSubscriptionImpl subscription =
                new TaskSubscriptionImpl(taskHandler, taskType, taskQueueId, lockTime, MAX_TASKS, taskAcquisition, autoCompleteTasks);
        subscription.open();
        return subscription;
    }


}
