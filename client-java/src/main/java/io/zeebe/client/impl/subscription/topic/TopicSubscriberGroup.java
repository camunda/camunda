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
package io.zeebe.client.impl.subscription.topic;

import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.client.api.subscription.*;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.record.GeneralRecordImpl;
import io.zeebe.client.impl.subscription.*;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class TopicSubscriberGroup extends SubscriberGroup<TopicSubscriber> implements TopicSubscription, PollableTopicSubscription
{
    private static final int MAX_HANDLING_RETRIES = 2;

    private AtomicBoolean processingFlag = new AtomicBoolean(false);
    private final TopicSubscriptionSpec subscription;

    public TopicSubscriberGroup(
            ActorControl actor,
            ZeebeClientImpl client,
            SubscriptionManager acquisition,
            TopicSubscriptionSpec subscription)
    {
        super(actor, client, acquisition, subscription.getTopic());
        this.subscription = subscription;
    }

    @Override
    public int poll()
    {
        return pollEvents(subscription.getHandler());
    }

    @Override
    public int poll(RecordHandler recordHandler)
    {
        return pollEvents((e) -> recordHandler.onRecord(e));
    }

    @Override
    public int pollEvents(CheckedConsumer<GeneralRecordImpl> pollHandler)
    {

        // ensuring at most one thread polls at a time which is the guarantee we give for
        // topic subscriptions
        if (processingFlag.compareAndSet(false, true))
        {
            try
            {
                return super.pollEvents(pollHandler);
            }
            finally
            {
                processingFlag.set(false);
            }
        }
        else
        {
            return 0;
        }
    }

    @Override
    public boolean isManagedGroup()
    {
        return subscription.isManaged();
    }

    @Override
    protected ActorFuture<? extends EventSubscriptionCreationResult> requestNewSubscriber(int partitionId)
    {
        return new CreateTopicSubscriptionCommandImpl(client.getCommandManager(), subscription.getTopic(), partitionId)
            .startPosition(subscription.getStartPosition(partitionId))
            .prefetchCapacity(subscription.getPrefetchCapacity())
            .name(subscription.getName())
            .forceStart(subscription.isForceStart())
            .executeAsync();
    }

    @Override
    protected TopicSubscriber buildSubscriber(EventSubscriptionCreationResult result)
    {
        return new TopicSubscriber(
                client,
                subscription,
                result.getSubscriberKey(),
                result.getEventPublisher(),
                result.getPartitionId(),
                this,
                subscriptionManager);
    }

    @Override
    protected ActorFuture<Void> doCloseSubscriber(TopicSubscriber subscriber)
    {
        final ActorFuture<?> ackFuture = subscriber.acknowledgeLastProcessedEvent();

        final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
        actor.runOnCompletion(ackFuture, (ackResult, ackThrowable) ->
        {
            if (ackThrowable != null)
            {
                Loggers.SUBSCRIPTION_LOGGER.error("Could not acknowledge last event position before closing subscriber. Ignoring.", ackThrowable);
            }

            final ActorFuture<Void> closeRequestFuture = subscriber.requestSubscriptionClose();
            actor.runOnCompletion(closeRequestFuture, (closeResult, closeThrowable) ->
            {
                if (closeThrowable == null)
                {
                    closeFuture.complete(closeResult);
                }
                else
                {
                    closeFuture.completeExceptionally(closeThrowable);
                }
            });
        });
        return closeFuture;
    }

    @Override
    protected String describeGroup()
    {
        return subscription.toString();
    }
}
