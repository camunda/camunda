package org.camunda.tngp.client.impl;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.cmd.FailAsyncTaskCmd;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.impl.cmd.PollAndLockTasksCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CloseTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CompleteTaskCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CreateTaskCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.FailTaskCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.UpdateSubscriptionCreditsCmdImpl;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;

public class TaskTopicClientImpl implements TaskTopicClient
{

    protected final int topicId;
    protected final TngpClientImpl client;

    public TaskTopicClientImpl(TngpClientImpl client, int topicId)
    {
        this.client = client;
        this.topicId = topicId;
    }

    @Override
    public CreateAsyncTaskCmd create()
    {
        return new CreateTaskCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
    }

    @Override
    public FailAsyncTaskCmd fail()
    {
        return new FailTaskCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
    }

    @Override
    public PollAndLockAsyncTasksCmd pollAndLock()
    {
        return new PollAndLockTasksCmdImpl(client.getCmdExecutor());
    }

    @Override
    public CompleteAsyncTaskCmd complete()
    {
        return new CompleteTaskCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
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
        return client.getSubscriptionManager().newTaskTopicSubscription(topicId);
    }

    public CreateTaskSubscriptionCmdImpl brokerTaskSubscription()
    {
        return new CreateTaskSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
    }

    public CloseTaskSubscriptionCmdImpl closeBrokerTaskSubscription()
    {
        return new CloseTaskSubscriptionCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
    }

    public UpdateSubscriptionCreditsCmdImpl updateSubscriptionCredits()
    {
        return new UpdateSubscriptionCreditsCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
    }


}
