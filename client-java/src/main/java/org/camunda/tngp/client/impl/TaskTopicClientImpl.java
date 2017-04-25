package org.camunda.tngp.client.impl;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.cmd.CompleteTaskCmd;
import org.camunda.tngp.client.cmd.CreateTaskCmd;
import org.camunda.tngp.client.cmd.FailTaskCmd;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.cmd.UpdateTaskRetriesCmd;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.impl.cmd.PollAndLockTasksCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CloseTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CompleteTaskCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CreateTaskCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.FailTaskCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.IncreaseTaskSubscriptionCreditsCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.UpdateTaskRetriesCmdImpl;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;

public class TaskTopicClientImpl implements TaskTopicClient
{

    protected final TngpClientImpl client;
    protected final String topicName;
    protected final int partitionId;

    public TaskTopicClientImpl(final TngpClientImpl client, final String topicName, final int partitionId)
    {
        this.client = client;
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    @Override
    public CreateTaskCmd create()
    {
        return new CreateTaskCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    @Override
    public FailTaskCmd fail()
    {
        return new FailTaskCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    @Override
    public UpdateTaskRetriesCmd updateRetries()
    {
        return new UpdateTaskRetriesCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    @Override
    public PollAndLockAsyncTasksCmd pollAndLock()
    {
        return new PollAndLockTasksCmdImpl(client.getCmdExecutor());
    }

    @Override
    public CompleteTaskCmd complete()
    {
        return new CompleteTaskCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    @Override
    public TaskSubscriptionBuilder newTaskSubscription()
    {
        return client.getSubscriptionManager().newTaskSubscription(this);
    }

    @Override
    public PollableTaskSubscriptionBuilder newPollableTaskSubscription()
    {
        return client.getSubscriptionManager().newPollableTaskSubscription(this);
    }

    @Override
    public TaskTopicSubscriptionBuilder newSubscription()
    {
        return client.getSubscriptionManager().newTaskTopicSubscription(topicName, partitionId);
    }

    public CreateTaskSubscriptionCmdImpl brokerTaskSubscription()
    {
        return new CreateTaskSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    public CloseTaskSubscriptionCmdImpl closeBrokerTaskSubscription()
    {
        return new CloseTaskSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl increaseSubscriptionCredits()
    {
        return new IncreaseTaskSubscriptionCreditsCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    public String getTopicName()
    {
        return topicName;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

}
