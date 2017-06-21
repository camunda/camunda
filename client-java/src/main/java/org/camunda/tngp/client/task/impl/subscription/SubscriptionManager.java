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
import org.camunda.tngp.util.actor.Actor;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerImpl;

public class SubscriptionManager implements TransportChannelListener
{

    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final EventAcquisition<TopicSubscriptionImpl> topicSubscriptionAcquisition;
    protected final SubscribedEventCollector taskCollector;
    protected final MsgPackMapper msgPackMapper;
    protected final TngpClientImpl client;

    protected final ActorScheduler actorScheduler;
    protected ActorReference aquisitionRef;
    protected ActorReference[] executorRefs;

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

        // allocate one thread per executor + one extra for acquisition
        this.actorScheduler = ActorSchedulerImpl.createDefaultScheduler(numExecutionThreads + 1);
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
        actorScheduler.close();
    }

    protected void startAcquisition()
    {
        if (aquisitionRef == null)
        {
            aquisitionRef = actorScheduler.schedule(new AquisitionActor());
        }
    }

    protected void stopAcquisition()
    {
        aquisitionRef.close();
        aquisitionRef = null;
    }

    protected void startExecution()
    {
        if (executorRefs == null)
        {
            executorRefs = new ActorReference[numExecutionThreads * 2];

            for (int i = 0; i < executorRefs.length; i += 2)
            {
                executorRefs[i] = actorScheduler.schedule(new SubscriptionExecutor(taskSubscriptions));
                executorRefs[i + 1] = actorScheduler.schedule(new SubscriptionExecutor(topicSubscriptions));
            }
        }
    }

    protected void stopExecution()
    {
        for (int i = 0; i < executorRefs.length; i++)
        {
            executorRefs[i].close();
        }

        executorRefs = null;
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

    public void onChannelClosed(int channelId)
    {
        taskSubscriptions.abortSubscriptionsOnChannel(channelId);
        topicSubscriptions.abortSubscriptionsOnChannel(channelId);
    }

    @Override
    public void onChannelClosed(Channel channel)
    {
        taskSubscriptions.abortSubscriptionsOnChannel(channel.getStreamId());
        topicSubscriptions.abortSubscriptionsOnChannel(channel.getStreamId());
    }

    private final class AquisitionActor implements Actor
    {
        @Override
        public int doWork() throws Exception
        {
            int workCount = 0;

            workCount += taskCollector.doWork();
            workCount += taskAcquisition.doWork();
            workCount += topicSubscriptionAcquisition.doWork();

            return workCount;
        }

        @Override
        public String name()
        {
            return "aquisition";
        }
    }

}
