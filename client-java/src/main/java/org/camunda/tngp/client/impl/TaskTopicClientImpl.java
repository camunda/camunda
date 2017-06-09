package org.camunda.tngp.client.impl;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.client.task.cmd.CompleteTaskCmd;
import org.camunda.tngp.client.task.cmd.CreateTaskCmd;
import org.camunda.tngp.client.task.cmd.FailTaskCmd;
import org.camunda.tngp.client.task.cmd.UpdateTaskRetriesCmd;
import org.camunda.tngp.client.task.impl.*;

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
