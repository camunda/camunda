package org.camunda.tngp.client.task.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.task.PollableTaskSubscription;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.util.EnsureUtil;

public class PollableTaskSubscriptionBuilderImpl implements PollableTaskSubscriptionBuilder
{

    protected String taskType;
    protected long lockTime = TimeUnit.MINUTES.toMillis(1);
    protected int taskQueueId = 0;

    protected final TaskAcquisition taskAcquisition;
    protected final boolean autoCompleteTasks;

    public PollableTaskSubscriptionBuilderImpl(TaskAcquisition taskAcquisition, boolean autoCompleteTasks)
    {
        this.taskAcquisition = taskAcquisition;
        this.autoCompleteTasks = autoCompleteTasks;
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
    public PollableTaskSubscriptionBuilder taskQueueId(int taskQueueId)
    {
        this.taskQueueId = taskQueueId;
        return this;
    }

    @Override
    public PollableTaskSubscription open()
    {
        EnsureUtil.ensureNotNull("taskType", taskType);
        EnsureUtil.ensureGreaterThan("lockTime", lockTime, 0L);

        final TaskSubscriptionImpl subscription =
                new TaskSubscriptionImpl(taskType, taskQueueId, lockTime, 1, taskAcquisition, autoCompleteTasks);
        subscription.open();
        return subscription;
    }
}
