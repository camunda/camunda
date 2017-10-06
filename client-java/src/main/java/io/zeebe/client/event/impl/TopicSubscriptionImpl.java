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
package io.zeebe.client.event.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.client.event.PollableTopicSubscription;
import io.zeebe.client.event.UniversalEventHandler;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.client.task.impl.subscription.EventSubscription;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.util.CheckedConsumer;

public class TopicSubscriptionImpl
    extends EventSubscription<TopicSubscriptionImpl>
    implements TopicSubscription, PollableTopicSubscription
{

    protected static final int MAX_HANDLING_RETRIES = 2;

    protected CheckedConsumer<GeneralEventImpl> handler;
    protected final TopicClientImpl client;

    protected AtomicBoolean processingFlag = new AtomicBoolean(false);
    protected volatile long lastProcessedEventPosition;
    protected long lastAcknowledgedPosition;

    protected final long startPosition;
    protected final boolean forceStart;
    protected final String name;
    protected final int prefetchCapacity;

    public TopicSubscriptionImpl(
            TopicClientImpl client,
            String topic,
            int partitionId,
            CheckedConsumer<GeneralEventImpl> handler,
            int prefetchCapacity,
            long startPosition,
            boolean forceStart,
            String name,
            EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        super(topic, partitionId, prefetchCapacity, acquisition);
        this.prefetchCapacity = prefetchCapacity;
        this.client = client;
        if (handler != null)
        {
            // default behavior for managed subscriptions
            this.handler = handler
                    .andThen(this::recordProcessedEvent)
                    .andOnExceptionRetry(MAX_HANDLING_RETRIES, this::logRetry)
                    .andOnException(this::logExceptionAndClose);
        }
        this.startPosition = startPosition;
        this.forceStart = forceStart;
        this.name = name;
        this.lastProcessedEventPosition = startPosition;
        this.lastAcknowledgedPosition = startPosition;
    }

    @Override
    public boolean isManagedSubscription()
    {
        return handler != null;
    }

    @Override
    public int poll()
    {
        return pollEvents(handler);
    }

    @Override
    public int poll(UniversalEventHandler taskHandler)
    {
        final CheckedConsumer<GeneralEventImpl> consumer = (e) -> taskHandler.handle(e);
        return pollEvents(consumer
                .andThen(this::recordProcessedEvent)
                .andOnException(this::logExceptionAndPropagate));
    }

    @Override
    public int pollEvents(CheckedConsumer<GeneralEventImpl> pollHandler)
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

    protected void logExceptionAndClose(GeneralEventImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Closing subscription.");
        this.closeAsync();
    }

    protected void logExceptionAndPropagate(GeneralEventImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Propagating exception to caller.");
        throw new RuntimeException(e);
    }

    protected void logRetry(GeneralEventImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Retrying.");
    }

    public CheckedConsumer<GeneralEventImpl> getHandler()
    {
        return handler;
    }

    @Override
    protected EventSubscriptionCreationResult requestNewSubscription()
    {
        return client.createTopicSubscription(topic, partitionId)
                .startPosition(startPosition)
                .prefetchCapacity(prefetchCapacity)
                .name(name)
                .forceStart(forceStart)
                .execute();
    }

    @Override
    protected void requestSubscriptionClose()
    {
        acknowledgeLastProcessedEvent();

        client.closeTopicSubscription(partitionId, subscriberKey).execute();
    }

    @Override
    protected void requestEventSourceReplenishment(int eventsProcessed)
    {
        acknowledgeLastProcessedEvent();
    }

    protected void acknowledgeLastProcessedEvent()
    {

        // note: it is important we read lastProcessedEventPosition only once
        //   as it be changed concurrently by an executor thread
        final long positionToAck = lastProcessedEventPosition;

        if (positionToAck > lastAcknowledgedPosition)
        {
            client.acknowledgeEvent(topic, partitionId)
                .subscriptionName(name)
                .ackPosition(positionToAck)
                .execute();

            lastAcknowledgedPosition = positionToAck;
        }
    }

    protected void recordProcessedEvent(GeneralEventImpl event)
    {
        this.lastProcessedEventPosition = event.getMetadata().getPosition();
    }

    protected void logEventHandlingError(Exception e, GeneralEventImpl event, String resolution)
    {
        LOGGER.error("Topic subscription " + name + ": Unhandled exception during handling of event " + event + ". " + resolution, e);
    }

    @Override
    public String toString()
    {
        return "TopicSubscriptionImpl [name=" + name + ", subscriberKey=" + subscriberKey + "]";
    }

}
