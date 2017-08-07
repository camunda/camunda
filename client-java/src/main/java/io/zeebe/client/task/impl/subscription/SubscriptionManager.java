/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.task.impl.subscription;

import io.zeebe.client.event.PollableTopicSubscriptionBuilder;
import io.zeebe.client.event.TopicSubscriptionBuilder;
import io.zeebe.client.event.impl.PollableTopicSubscriptionBuilderImpl;
import io.zeebe.client.event.impl.TopicClientImpl;
import io.zeebe.client.event.impl.TopicSubscriptionBuilderImpl;
import io.zeebe.client.event.impl.TopicSubscriptionImpl;
import io.zeebe.client.impl.TasksClientImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.data.MsgPackMapper;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.transport.ClientInputMessageSubscription;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportListener;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;

public class SubscriptionManager implements TransportListener, Actor
{
    protected final EventAcquisition<TaskSubscriptionImpl> taskAcquisition;
    protected final EventAcquisition<TopicSubscriptionImpl> topicSubscriptionAcquisition;
    protected final ClientInputMessageSubscription messageSubscription;
    protected final MsgPackMapper msgPackMapper;

    protected final ActorScheduler executorActorScheduler;
    protected final ActorScheduler acquisitionActorScheduler;

    protected ActorReference[] acquisitionActorRefs;
    protected ActorReference[] executorActorRefs;

    protected final int numExecutionThreads;

    protected final EventSubscriptions<TaskSubscriptionImpl> taskSubscriptions;
    protected final EventSubscriptions<TopicSubscriptionImpl> topicSubscriptions;

    // task-subscription-specific config
    protected final boolean autoCompleteTasks;

    // topic-subscription specific config
    protected final int topicSubscriptionPrefetchCapacity;

    public SubscriptionManager(
            ZeebeClientImpl client,
            int numExecutionThreads,
            boolean autoCompleteTasks,
            int topicSubscriptionPrefetchCapacity)
    {
        this.taskSubscriptions = new EventSubscriptions<>();
        this.topicSubscriptions = new EventSubscriptions<>();


        this.taskAcquisition = new EventAcquisition<>("task-acquisition", taskSubscriptions);
        this.topicSubscriptionAcquisition = new EventAcquisition<>("topic-event-acquisition", topicSubscriptions);

        final SubscribedEventCollector taskCollector = new SubscribedEventCollector(
                taskAcquisition,
                topicSubscriptionAcquisition,
                client.getMsgPackConverter());
        this.messageSubscription = client.getTransport()
                .openSubscription("event-acquisition", taskCollector)
                .join();

        this.numExecutionThreads = numExecutionThreads;
        this.autoCompleteTasks = autoCompleteTasks;
        this.msgPackMapper = new MsgPackMapper(client.getObjectMapper());

        this.topicSubscriptionPrefetchCapacity = topicSubscriptionPrefetchCapacity;

        this.acquisitionActorScheduler = ActorSchedulerBuilder.createDefaultScheduler("acquisition");
        this.executorActorScheduler = ActorSchedulerBuilder.createDefaultScheduler("executors", numExecutionThreads);
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

            acquisitionActorRefs[0] = acquisitionActorScheduler.schedule(this);
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

    public TaskSubscriptionBuilder newTaskSubscription(TasksClientImpl client, String topic)
    {
        final int partitionId = 0; // TODO: will be based on cluster topology and assignment in the future
        return new TaskSubscriptionBuilderImpl(client, topic, partitionId, taskAcquisition, autoCompleteTasks, msgPackMapper);
    }

    public PollableTaskSubscriptionBuilder newPollableTaskSubscription(TasksClientImpl client, String topic)
    {
        final int partitionId = 0; // TODO: will be based on cluster topology and assignment in the future
        return new PollableTaskSubscriptionBuilderImpl(client, topic, partitionId, taskAcquisition, autoCompleteTasks, msgPackMapper);
    }

    public TopicSubscriptionBuilder newTopicSubscription(TopicClientImpl client, String topic)
    {
        final int partitionId = 0; // TODO: will be based on cluster topology and assignment in the future
        return new TopicSubscriptionBuilderImpl(client, topic, partitionId, topicSubscriptionAcquisition, msgPackMapper, topicSubscriptionPrefetchCapacity);
    }

    public PollableTopicSubscriptionBuilder newPollableTopicSubscription(TopicClientImpl client, String topic)
    {
        final int partitionId = 0; // TODO: will be based on cluster topology and assignment in the future
        return new PollableTopicSubscriptionBuilderImpl(client, topic, partitionId, topicSubscriptionAcquisition, topicSubscriptionPrefetchCapacity);
    }

    @Override
    public void onConnectionEstablished(RemoteAddress remoteAddress)
    {

    }

    @Override
    public void onConnectionClosed(RemoteAddress remoteAddress)
    {
        taskSubscriptions.reopenSubscriptionsForRemote(remoteAddress);
        topicSubscriptions.reopenSubscriptionsForRemote(remoteAddress);
    }

    @Override
    public int doWork() throws Exception
    {
        return messageSubscription.poll();
    }
}
