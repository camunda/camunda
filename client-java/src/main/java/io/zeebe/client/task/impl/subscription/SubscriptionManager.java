package io.zeebe.client.task.impl.subscription;

import io.zeebe.client.event.PollableTopicSubscriptionBuilder;
import io.zeebe.client.event.TopicSubscriptionBuilder;
import io.zeebe.client.event.impl.*;
import io.zeebe.client.impl.TaskTopicClientImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.Channel;
import io.zeebe.transport.TransportChannelListener;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;
import org.agrona.concurrent.AgentRunner;

public class SubscriptionManager implements TransportChannelListener
{

    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final EventAcquisition<TopicSubscriptionImpl> topicSubscriptionAcquisition;
    protected final SubscribedEventCollector taskCollector;
    protected final MsgPackMapper msgPackMapper;
    protected final ZeebeClientImpl client;
    protected final ActorScheduler actorScheduler;

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
            ZeebeClientImpl client,
            final ActorScheduler actorScheduler,
            int numExecutionThreads,
            boolean autoCompleteTasks,
            int topicSubscriptionPrefetchCapacity,
            Subscription receiveBufferSubscription)
    {
        this.client = client;
        this.actorScheduler = actorScheduler;
        this.taskSubscriptions = new EventSubscriptions<>();
        this.topicSubscriptions = new EventSubscriptions<>();

        this.taskAcquisition = new EventAcquisition<>("task-acquisition", taskSubscriptions, client.getChannelManager());
        this.topicSubscriptionAcquisition = new EventAcquisition<>("topic-event-acquisition", topicSubscriptions, client.getChannelManager());
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
        if (acquisitionActorRefs == null)
        {
            acquisitionActorRefs = new ActorReference[3];

            acquisitionActorRefs[0] = actorScheduler.schedule(taskCollector);
            acquisitionActorRefs[1] = actorScheduler.schedule(taskAcquisition);
            acquisitionActorRefs[2] = actorScheduler.schedule(topicSubscriptionAcquisition);
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
                executorActorRefs[i] = actorScheduler.schedule(new SubscriptionExecutor(taskSubscriptions));
                executorActorRefs[i + 1] = actorScheduler.schedule(new SubscriptionExecutor(topicSubscriptions));
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
