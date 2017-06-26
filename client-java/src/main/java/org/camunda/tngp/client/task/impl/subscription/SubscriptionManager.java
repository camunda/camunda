package org.camunda.tngp.client.task.impl.subscription;

import org.agrona.concurrent.AgentRunner;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;
import org.camunda.tngp.client.event.impl.*;
import org.camunda.tngp.client.impl.TaskTopicClientImpl;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.data.MsgPackMapper;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.TransportChannelListener;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerBuilder;

public class SubscriptionManager implements TransportChannelListener
{

    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final EventAcquisition<TopicSubscriptionImpl> topicSubscriptionAcquisition;
    protected final SubscribedEventCollector taskCollector;
    protected final MsgPackMapper msgPackMapper;
    protected final TngpClientImpl client;

    protected final ActorScheduler executorActorScheduler;
    protected final ActorScheduler acquisitionActorScheduler;

    protected ActorReference[] acquisitionActorRefs;
    protected ActorReference[] executorActorRefs;

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

        this.acquisitionActorScheduler = ActorSchedulerBuilder.createDefaultScheduler();
        this.executorActorScheduler = ActorSchedulerBuilder.createDefaultScheduler(numExecutionThreads);
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

    public void close()
    {
        acquisitionActorScheduler.close();
        executorActorScheduler.close();
    }

    protected void startAcquisition()
    {
        if (acquisitionActorRefs == null)
        {
            acquisitionActorRefs = new ActorReference[3];

            acquisitionActorRefs[0] = acquisitionActorScheduler.schedule(taskCollector);
            acquisitionActorRefs[1] = acquisitionActorScheduler.schedule(taskAcquisition);
            acquisitionActorRefs[2] = acquisitionActorScheduler.schedule(topicSubscriptionAcquisition);
        }
    }

    protected void stopAcquisition()
    {
        for (int i = 0; i < acquisitionActorRefs.length; i++)
        {
            acquisitionActorRefs[i].close();
        }

        acquisitionActorRefs = null;
    }

    protected void startExecution()
    {
        if (executorActorRefs == null)
        {
            executorActorRefs = new ActorReference[numExecutionThreads * 2];

            for (int i = 0; i < executorActorRefs.length; i += 2)
            {
                executorActorRefs[i] = executorActorScheduler.schedule(new SubscriptionExecutor(taskSubscriptions));
                executorActorRefs[i + 1] = executorActorScheduler.schedule(new SubscriptionExecutor(topicSubscriptions));
            }
        }
    }

    protected void stopExecution()
    {
        for (int i = 0; i < executorActorRefs.length; i++)
        {
            executorActorRefs[i].close();
        }

        executorActorRefs = null;
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
        return new TopicSubscriptionBuilderImpl(client, topicSubscriptionAcquisition, msgPackMapper, topicSubscriptionPrefetchCapacity);
    }

    public PollableTopicSubscriptionBuilder newPollableTopicSubscription(TopicClientImpl client)
    {
        return new PollableTopicSubscriptionBuilderImpl(client, topicSubscriptionAcquisition, topicSubscriptionPrefetchCapacity);
    }

    @Override
    public void onChannelClosed(Channel channel)
    {
        taskSubscriptions.abortSubscriptionsOnChannel(channel.getStreamId());
        topicSubscriptions.abortSubscriptionsOnChannel(channel.getStreamId());
    }

    @Override
    public void onChannelInterrupted(Channel channel)
    {
        taskSubscriptions.suspendSubscriptionsOnChannel(channel.getStreamId());
        topicSubscriptions.suspendSubscriptionsOnChannel(channel.getStreamId());
    }

    @Override
    public void onChannelOpened(Channel channel)
    {
        taskSubscriptions.reopenSubscriptionsOnChannel(channel.getStreamId());
        topicSubscriptions.reopenSubscriptionsOnChannel(channel.getStreamId());
    }
}
