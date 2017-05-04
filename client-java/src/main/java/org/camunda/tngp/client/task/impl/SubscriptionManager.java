package org.camunda.tngp.client.task.impl;

import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.CompositeAgent;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;
import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.PollableTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TaskTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicClientImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
import org.camunda.tngp.client.impl.TaskTopicClientImpl;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.data.MsgPackMapper;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.TransportChannelListener;

public class SubscriptionManager implements TransportChannelListener
{

    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final EventAcquisition<TopicSubscriptionImpl> topicSubscriptionAcquisition;
    protected final SubscribedEventCollector taskCollector;
    protected final MsgPackMapper msgPackMapper;
    protected final TngpClientImpl client;

    protected AgentRunner acquisitionRunner;
    protected AgentRunner[] executionRunners;
    protected final int numExecutionThreads;

    protected final EventSubscriptions<TaskSubscriptionImpl> taskSubscriptions;
    protected final EventSubscriptions<TopicSubscriptionImpl> topicSubscriptions;

    // task-subscription-specific config
    protected final boolean autoCompleteTasks;

    // topic-subscription specific config
    protected final int topicSubscriptionPrefetchCapacity;

    public SubscriptionManager(
            TngpClientImpl client,
            int numExecutionThreads,
            boolean autoCompleteTasks,
            int topicSubscriptionPrefetchCapacity,
            Subscription receiveBufferSubscription)
    {
        this.client = client;
        this.taskSubscriptions = new EventSubscriptions<>();
        this.topicSubscriptions = new EventSubscriptions<>();

        this.taskAcquisition = new EventAcquisition<>("task-acquisition", taskSubscriptions);
        this.topicSubscriptionAcquisition = new EventAcquisition<>("topic-event-acquisition", topicSubscriptions);
        this.taskCollector = new SubscribedEventCollector(receiveBufferSubscription, taskAcquisition, topicSubscriptionAcquisition);

        this.numExecutionThreads = numExecutionThreads;
        this.autoCompleteTasks = autoCompleteTasks;
        this.msgPackMapper = new MsgPackMapper(client.getObjectMapper());

        this.topicSubscriptionPrefetchCapacity = topicSubscriptionPrefetchCapacity;
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
            acquisitionRunner = newAgentRunner(new CompositeAgent(taskCollector, taskAcquisition, topicSubscriptionAcquisition));
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
                executionRunners[i] = newAgentRunner(new CompositeAgent(new SubscriptionExecutor(taskSubscriptions), new SubscriptionExecutor(topicSubscriptions)));
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
        this.taskSubscriptions.closeAll();
        this.topicSubscriptions.closeAll();
    }

    public TaskSubscriptionBuilder newTaskSubscription(TaskTopicClientImpl client)
    {
        return new TaskSubscriptionBuilderImpl(client, taskAcquisition, autoCompleteTasks, msgPackMapper);
    }

    public PollableTaskSubscriptionBuilder newPollableTaskSubscription(TaskTopicClientImpl client)
    {
        return new PollableTaskSubscriptionBuilderImpl(client, taskAcquisition, autoCompleteTasks, msgPackMapper);
    }

    public TopicSubscriptionBuilder newTopicSubscription(TopicClientImpl client)
    {
        return new TopicSubscriptionBuilderImpl(client, topicSubscriptionAcquisition, topicSubscriptionPrefetchCapacity);
    }

    public PollableTopicSubscriptionBuilder newPollableTopicSubscription(TopicClientImpl client)
    {
        return new PollableTopicSubscriptionBuilderImpl(client, topicSubscriptionAcquisition, topicSubscriptionPrefetchCapacity);
    }

    public TaskTopicSubscriptionBuilder newTaskTopicSubscription(final String topicName, final int partitionId)
    {
        return new TaskTopicSubscriptionBuilderImpl(client.topic(topicName, partitionId), topicSubscriptionAcquisition, msgPackMapper, topicSubscriptionPrefetchCapacity);
    }

    public void onChannelClosed(int channelId)
    {
        taskSubscriptions.abortSubscriptionsOnChannel(channelId);
        topicSubscriptions.abortSubscriptionsOnChannel(channelId);
    }

    @Override
    public void onChannelClosed(TransportChannel channel)
    {
        taskSubscriptions.abortSubscriptionsOnChannel(channel.getId());
        topicSubscriptions.abortSubscriptionsOnChannel(channel.getId());
    }

}
