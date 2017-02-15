package org.camunda.tngp.client.task.impl;

import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.dispatcher.Subscription;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskSubscriptionManager
{

    protected final TaskAcquisition acqusition;
    protected AgentRunner acquisitionRunner;
    protected AgentRunner[] executionRunners;
    protected final int numExecutionThreads;

    protected final TaskSubscriptions subscriptions;

    protected final boolean autoCompleteTasks;

    public TaskSubscriptionManager(TngpClientImpl client, int numExecutionThreads, boolean autoCompleteTasks, Subscription receiveBufferSubscription, ObjectMapper objectMapper)
    {
        subscriptions = new TaskSubscriptions();

        final TaskDataFrameCollector taskCollector = new TaskDataFrameCollector(receiveBufferSubscription, objectMapper);

        acqusition = new TaskAcquisition(client, subscriptions, taskCollector);
        this.numExecutionThreads = numExecutionThreads;

        this.autoCompleteTasks = autoCompleteTasks;
    }

    public void start()
    {
        startAcquisition();
        startExecution();
    }

    public void stop()
    {
        stopAcquisition();
        stopExecution();
    }

    protected void startAcquisition()
    {
        if (acquisitionRunner == null)
        {
            acquisitionRunner = newAgentRunner(acqusition);
        }

        AgentRunner.startOnThread(acquisitionRunner);
    }

    protected void stopAcquisition()
    {
        acquisitionRunner.close();
        acquisitionRunner = null;
    }

    protected void startExecution()
    {
        if (executionRunners == null)
        {
            executionRunners = new AgentRunner[numExecutionThreads];
            for (int i = 0; i < executionRunners.length; i++)
            {
                executionRunners[i] = newAgentRunner(new TaskExecutor(subscriptions));
            }
        }

        for (int i = 0; i < executionRunners.length; i++)
        {
            AgentRunner.startOnThread(executionRunners[i]);
        }
    }

    protected void stopExecution()
    {
        for (int i = 0; i < executionRunners.length; i++)
        {
            executionRunners[i].close();
        }
        executionRunners = null;
    }

    protected static AgentRunner newAgentRunner(Agent agent)
    {
        return new AgentRunner(
            new BackoffIdleStrategy(1000, 100, 100, TimeUnit.MILLISECONDS.toNanos(10)),
            (e) -> e.printStackTrace(),
            null,
            agent);
    }

    public void closeAllSubscriptions()
    {
        this.subscriptions.closeAll();
    }

    public TaskSubscriptionBuilder newSubscription()
    {
        return new TaskSubscriptionBuilderImpl(acqusition, autoCompleteTasks);
    }

    public PollableTaskSubscriptionBuilder newPollableSubscription()
    {
        return new PollableTaskSubscriptionBuilderImpl(acqusition, autoCompleteTasks);
    }

}
