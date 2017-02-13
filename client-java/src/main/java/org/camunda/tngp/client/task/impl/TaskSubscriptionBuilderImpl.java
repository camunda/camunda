package org.camunda.tngp.client.task.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.util.EnsureUtil;

public class TaskSubscriptionBuilderImpl implements TaskSubscriptionBuilder
{

    public static final int DEFAULT_TASK_PREFETCH_SIZE = 32;

    protected String taskType;
    protected long lockTime = TimeUnit.MINUTES.toMillis(1);
    protected int lockOwner = -1;
    protected int topicId = 0;
    protected TaskHandler taskHandler;
    protected int taskFetchSize = DEFAULT_TASK_PREFETCH_SIZE;

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
    public TaskSubscriptionBuilder topicId(int taskQueueId)
    {
        this.topicId = taskQueueId;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder handler(TaskHandler handler)
    {
        this.taskHandler = handler;
        return this;
    }

    public TaskSubscriptionBuilder taskFetchSize(int numTasks)
    {
        this.taskFetchSize = numTasks;
        return this;
    }

    @Override
    public TaskSubscriptionBuilder lockOwner(int lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    @Override
    public TaskSubscriptionImpl open()
    {
        EnsureUtil.ensureNotNull("taskHandler", taskHandler);
        EnsureUtil.ensureNotNull("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureGreaterThanOrEqual("lockOwner", lockOwner, 0);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionImpl subscription =
                new TaskSubscriptionImpl(taskHandler, taskType, topicId, lockTime, lockOwner, taskFetchSize, taskAcquisition, autoCompleteTasks);
        subscription.open();
        return subscription;
    }

}
