package org.camunda.tngp.client.task.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.impl.data.MsgPackMapper;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.util.EnsureUtil;

public class TaskSubscriptionBuilderImpl implements TaskSubscriptionBuilder
{
    public static final int DEFAULT_TASK_FETCH_SIZE = 32;

    protected String taskType;
    protected long lockTime = TimeUnit.MINUTES.toMillis(1);
    protected int lockOwner = -1;
    protected int topicId = 0;
    protected TaskHandler taskHandler;
    protected int taskFetchSize = DEFAULT_TASK_FETCH_SIZE;

    protected final AsyncTasksClient taskClient;
    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final boolean autoCompleteTasks;
    protected final MsgPackMapper msgPackMapper;

    public TaskSubscriptionBuilderImpl(AsyncTasksClient taskClient,
            EventAcquisition<TaskSubscriptionImpl> taskAcquisition,
            boolean autoCompleteTasks,
            MsgPackMapper msgPackMapper)
    {
        this.taskClient = taskClient;
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
        EnsureUtil.ensureNotNull("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureGreaterThanOrEqual("lockOwner", lockOwner, 0);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionImpl subscription =
                new TaskSubscriptionImpl(
                        taskClient,
                        taskHandler,
                        taskType,
                        topicId,
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
