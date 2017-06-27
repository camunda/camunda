package io.zeebe.client.task.impl.subscription;

import java.time.Duration;

import io.zeebe.client.impl.TaskTopicClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.util.EnsureUtil;

public class PollableTaskSubscriptionBuilderImpl implements PollableTaskSubscriptionBuilder
{

    protected int taskFetchSize = TaskSubscriptionBuilderImpl.DEFAULT_TASK_FETCH_SIZE;
    protected String taskType;
    protected long lockTime = Duration.ofMinutes(1).toMillis();
    protected String lockOwner;

    protected final TaskTopicClientImpl taskClient;
    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final boolean autoCompleteTasks;
    protected final MsgPackMapper msgPackMapper;

    public PollableTaskSubscriptionBuilderImpl(
            TaskTopicClientImpl taskClient,
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
    public PollableTaskSubscriptionBuilder taskType(String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilder lockTime(long lockDuration)
    {
        this.lockTime = lockDuration;
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilder lockTime(Duration lockDuration)
    {
        return lockTime(lockDuration.toMillis());
    }

    @Override
    public PollableTaskSubscriptionBuilder lockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    @Override
    public PollableTaskSubscriptionBuilderImpl taskFetchSize(int numTasks)
    {
        this.taskFetchSize = numTasks;
        return this;
    }

    @Override
    public PollableTaskSubscription open()
    {
        EnsureUtil.ensureNotNullOrEmpty("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);
        EnsureUtil.ensureNotNullOrEmpty("lockOwner", lockOwner);
        EnsureUtil.ensureGreaterThan("taskFetchSize", taskFetchSize, 0);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(
                taskClient,
                null,
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
