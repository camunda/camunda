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
package io.zeebe.client.impl.subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.record.GeneralRecordImpl;
import io.zeebe.client.impl.record.RecordMetadataImpl;
import io.zeebe.client.impl.subscription.job.JobSubscriberGroup;
import io.zeebe.client.impl.subscription.job.JobSubscriptionSpec;
import io.zeebe.client.impl.subscription.topic.*;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.transport.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.*;
import org.slf4j.Logger;

public class SubscriptionManager extends Actor implements SubscribedEventHandler, TransportListener
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    protected final ZeebeClientImpl client;

    private final EventSubscribers taskSubscribers = new EventSubscribers();
    private final EventSubscribers topicSubscribers = new EventSubscribers();

    final IdleStrategy idleStrategy = new BackoffIdleStrategy(1000, 100, 1, TimeUnit.MILLISECONDS.toNanos(1));
    final ErrorHandler errorHandler = Throwable::printStackTrace;

    private final List<AgentRunner> agentRunners = new ArrayList<>();

    private boolean isClosing = false;
    private ClientInputMessageSubscription incomingEventSubscription;

    public SubscriptionManager(ZeebeClientImpl client)
    {
        this.client = client;
    }

    @Override
    protected void onActorStarting()
    {
        final SubscribedEventCollector taskCollector = new SubscribedEventCollector(
                this,
                client.getMsgPackConverter(),
                client.getObjectMapper());

        actor.runOnCompletion(
            client.getTransport().openSubscription("event-acquisition", taskCollector),
            (s, t) -> incomingEventSubscription = s);
    }

    @Override
    protected void onActorStarted()
    {
        actor.consume(incomingEventSubscription, () ->
        {
            if (incomingEventSubscription.poll() == 0)
            {
                actor.yield();
            }
        });
        startSubscriptionExecution(client.getConfiguration().getNumSubscriptionExecutionThreads());
    }

    private void startSubscriptionExecution(int numThreads)
    {
        for (int i = 0; i < numThreads; i++)
        {
            final SubscriptionExecutor executor = new SubscriptionExecutor(topicSubscribers, taskSubscribers);
            final AgentRunner agentRunner = initAgentRunner(executor);
            AgentRunner.startOnThread(agentRunner);

            agentRunners.add(agentRunner);
        }
    }

    private void stopSubscriptionExecution()
    {
        for (AgentRunner runner: agentRunners)
        {
            runner.close();
        }
    }

    private AgentRunner initAgentRunner(Agent agent)
    {
        return new AgentRunner(idleStrategy, errorHandler, null, agent);
    }

    @Override
    protected void onActorClosing()
    {
        closeAllSubscribers("Subscription manager shutdown");

        stopSubscriptionExecution();
    }

    @Override
    protected void onActorCloseRequested()
    {
        this.isClosing = true;
    }

    public boolean isClosing()
    {
        return isClosing;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public ActorFuture<TopicSubscriberGroup> openTopicSubscription(TopicSubscriptionSpec spec)
    {
        final CompletableActorFuture<TopicSubscriberGroup> future = new CompletableActorFuture<>();
        actor.call(() ->
        {
            final TopicSubscriberGroup group = new TopicSubscriberGroup(actor, client, this, spec);
            topicSubscribers.addGroup(group);
            group.open((CompletableActorFuture) future);
        });

        return future;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public ActorFuture<JobSubscriberGroup> openJobSubscription(JobSubscriptionSpec spec)
    {
        final CompletableActorFuture<JobSubscriberGroup> future = new CompletableActorFuture<>();
        actor.call(() ->
        {
            final JobSubscriberGroup group = new JobSubscriberGroup(actor, client, this, spec);
            taskSubscribers.addGroup(group);
            group.open((CompletableActorFuture) future);
        });

        return future;
    }

    public void addSubscriber(Subscriber subscriber)
    {
        if (subscriber instanceof TopicSubscriber)
        {
            topicSubscribers.add(subscriber);
        }
        else
        {
            taskSubscribers.add(subscriber);
        }

    }

    public void removeSubscriber(Subscriber subscriber)
    {
        if (subscriber instanceof TopicSubscriber)
        {
            topicSubscribers.remove(subscriber);
        }
        else
        {
            taskSubscribers.remove(subscriber);
        }
    }

    public void closeAllSubscribers(String reason)
    {
        topicSubscribers.closeAllGroups(reason);
        taskSubscribers.closeAllGroups(reason);
    }

    public ActorFuture<Void> reopenSubscriptionsForRemoteAsync(RemoteAddress remoteAddress)
    {
        return actor.call(() ->
        {
            topicSubscribers.reopenSubscribersForRemote(remoteAddress);
            taskSubscribers.reopenSubscribersForRemote(remoteAddress);
        });
    }

    public ActorFuture<Void> closeGroup(SubscriberGroup<?> group, String reason)
    {
        final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
        actor.call(() ->
        {
            group.listenForClose(closeFuture);
            group.initClose(reason, null);
        });
        return closeFuture;
    }

    @Override
    public boolean onEvent(SubscriptionType type, long subscriberKey, GeneralRecordImpl event)
    {
        final RecordMetadataImpl eventMetadata = event.getMetadata();

        final EventSubscribers subscribers;

        if (type == SubscriptionType.JOB_SUBSCRIPTION)
        {
            subscribers = taskSubscribers;
        }
        else if (type == SubscriptionType.TOPIC_SUBSCRIPTION)
        {
            subscribers = topicSubscribers;
        }
        else
        {
            subscribers = null;
        }

        Subscriber subscriber = null;

        if (subscribers != null)
        {
            final int partitionId = eventMetadata.getPartitionId();
            subscriber = subscribers.getSubscriber(partitionId, subscriberKey);

            if (subscriber == null)
            {
                if (subscribers.isAnySubscriberOpeningOn(partitionId))
                {
                    // Avoids a race condition when a subscribe request was successful
                    // and we haven't processed the response yet (=> therefore subscriber is not registered yet),
                    // but we already receive an event from the broke.
                    // In this case, we postpone the event.
                    return false;
                }
            }
        }

        if (subscriber != null && subscriber.isOpen())
        {
            event.setTopicName(subscriber.getTopicName());
            return subscriber.addEvent(event);
        }
        else
        {
            LOGGER.debug("Ignoring event event {} for subscription [type={}, partition={}, key={}]",
                    event, type, event.getMetadata().getPartitionId(), subscriberKey);
            return true; // ignoring the event is success; don't want to retry it later
        }
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }


    @Override
    public void onConnectionEstablished(RemoteAddress remoteAddress)
    {
    }


    @Override
    public void onConnectionClosed(RemoteAddress remoteAddress)
    {
        reopenSubscriptionsForRemoteAsync(remoteAddress);
    }

}
