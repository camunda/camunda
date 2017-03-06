package org.camunda.tngp.client.task.impl;

import java.time.Duration;

import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.impl.TaskTopicClientImpl;
import org.camunda.tngp.client.impl.data.MsgPackMapper;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.util.EnsureUtil;

public class TaskSubscriptionBuilderImpl implements TaskSubscriptionBuilder
{
    public static final int DEFAULT_TASK_FETCH_SIZE = 32;

    protected String taskType;
    protected long lockTime = Duration.ofMinutes(1).toMillis();
    protected int lockOwner = -1;
    protected TaskHandler taskHandler;
    protected int taskFetchSize = DEFAULT_TASK_FETCH_SIZE;

    protected final TaskTopicClientImpl client;
    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final boolean autoCompleteTasks;
    protected final MsgPackMapper msgPackMapper;

    public TaskSubscriptionBuilderImpl(
            TaskTopicClientImpl client,
            EventAcquisition<TaskSubscriptionImpl> taskAcquisition,
            boolean autoCompleteTasks,
            MsgPackMapper msgPackMapper)
    {
        this.client = client;
        this.taskAcquisition = taskAcquisition;
        this.autoCompleteTasks = autoCompleteTasks;
        this.msgPackMapper = msgPackMapper;
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
    public TaskSubscriptionBuilder handler(TaskHandler handler)
    {
        this.taskHandler = handler;
        return this;
    }

    @Override
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
        EnsureUtil.ensureNotNullOrEmpty("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureGreaterThanOrEqual("lockOwner", lockOwner, 0);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionImpl subscription =
                new TaskSubscriptionImpl(
                        client,
                        taskHandler,
                        taskType,
                        lockTime,
                        lockOwner,
                        taskFetchSize,
                        taskAcquisition,
                        msgPackMapper,
                        autoCompleteTasks);

        subscription.open();
        return subscription;
    }

}
