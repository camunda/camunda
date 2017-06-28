package org.camunda.tngp.client.impl;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.client.task.cmd.CompleteTaskCmd;
import org.camunda.tngp.client.task.cmd.CreateTaskCmd;
import org.camunda.tngp.client.task.cmd.FailTaskCmd;
import org.camunda.tngp.client.task.cmd.UpdateTaskRetriesCmd;
import org.camunda.tngp.client.task.impl.CloseTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.task.impl.CompleteTaskCmdImpl;
import org.camunda.tngp.client.task.impl.CreateTaskCmdImpl;
import org.camunda.tngp.client.task.impl.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.task.impl.FailTaskCmdImpl;
import org.camunda.tngp.client.task.impl.IncreaseTaskSubscriptionCreditsCmdImpl;
import org.camunda.tngp.client.task.impl.UpdateTaskRetriesCmdImpl;

public class TaskTopicClientImpl implements TaskTopicClient
{

    protected final TngpClientImpl client;
    protected final Topic topic;

    public TaskTopicClientImpl(final TngpClientImpl client, final String topicName, final int partitionId)
    {
        this.client = client;
        this.topic = new Topic(topicName, partitionId);
    }

    @Override
    public CreateTaskCmd create()
    {
        return new CreateTaskCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    @Override
    public FailTaskCmd fail()
    {
        return new FailTaskCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    @Override
    public UpdateTaskRetriesCmd updateRetries()
    {
        return new UpdateTaskRetriesCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    @Override
    public CompleteTaskCmd complete()
    {
        return new CompleteTaskCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
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
        return new CreateTaskSubscriptionCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    public CloseTaskSubscriptionCmdImpl closeBrokerTaskSubscription()
    {
        return new CloseTaskSubscriptionCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl increaseSubscriptionCredits()
    {
        return new IncreaseTaskSubscriptionCreditsCmdImpl(client.getCommandManager(), client.getObjectMapper(), topic);
    }

    public Topic getTopic()
    {
        return topic;
    }

}
