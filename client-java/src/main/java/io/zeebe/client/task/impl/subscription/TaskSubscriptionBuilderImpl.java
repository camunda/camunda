package io.zeebe.client.task.impl.subscription;

import java.time.Duration;

import io.zeebe.client.impl.TaskTopicClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.util.EnsureUtil;

public class TaskSubscriptionBuilderImpl implements TaskSubscriptionBuilder
{
    public static final int DEFAULT_TASK_FETCH_SIZE = 32;

    protected String taskType;
    protected long lockTime = Duration.ofMinutes(1).toMillis();
    protected String lockOwner;
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
    public TaskSubscriptionBuilder lockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    @Override
    public TaskSubscriptionImpl open()
    {
        EnsureUtil.ensureNotNull("taskHandler", taskHandler);
        EnsureUtil.ensureNotNullOrEmpty("lockOwner", lockOwner);
        EnsureUtil.ensureNotNullOrEmpty("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(
                client,
                taskHandler,
                taskType,
                lockTime,
                lockOwner,
                taskFetchSize,
                msgPackMapper,
                autoCompleteTasks,
                taskAcquisition);

        taskAcquisition.newSubscriptionAsync(subscription);

        subscription.open();

        return subscription;
    }

}
