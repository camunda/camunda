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

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.client.task.impl.subscription.EventSubscriber;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.util.CheckedConsumer;

public class TopicSubscriber extends EventSubscriber
{

    protected static final int MAX_HANDLING_RETRIES = 2;

    protected final TopicClientImpl client;

    protected AtomicBoolean processingFlag = new AtomicBoolean(false);
    protected volatile long lastProcessedEventPosition;
    protected long lastAcknowledgedPosition;

    protected final TopicSubscriptionSpec subscription;

    protected final Function<CheckedConsumer<GeneralEventImpl>, CheckedConsumer<GeneralEventImpl>> eventHandlerAdapter;

    public TopicSubscriber(
            TopicClientImpl client,
            TopicSubscriptionSpec subscription,
            int partitionId,
            EventAcquisition acquisition)
    {
        super(partitionId, subscription.getPrefetchCapacity(), acquisition);
        this.subscription = subscription;
        this.client = client;
        this.lastProcessedEventPosition = subscription.getStartPosition(partitionId);
        this.lastAcknowledgedPosition = subscription.getStartPosition(partitionId);

        if (subscription.isManaged())
        {
            eventHandlerAdapter = h -> h
                .andThen(this::recordProcessedEvent)
                .andOnExceptionRetry(MAX_HANDLING_RETRIES, this::logRetry)
                .andOnException(this::logExceptionAndClose);
        }
        else
        {
            eventHandlerAdapter = h -> h
                .andThen(this::recordProcessedEvent)
                .andOnException(this::logExceptionAndPropagate);
        }

    }

    public int pollEvents(CheckedConsumer<GeneralEventImpl> consumer)
    {
        return super.pollEvents(eventHandlerAdapter.apply(consumer));
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

    @Override
    protected Future<? extends EventSubscriptionCreationResult> requestNewSubscription()
    {
        return client.createTopicSubscription(subscription.getTopic(), partitionId)
                .startPosition(subscription.getStartPosition(partitionId))
                .prefetchCapacity(subscription.getPrefetchCapacity())
                .name(subscription.getName())
                .forceStart(subscription.isForceStart())
                .executeAsync();
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
        //   as it can be changed concurrently by an executor thread
        final long positionToAck = lastProcessedEventPosition;

        if (positionToAck > lastAcknowledgedPosition)
        {
            client.acknowledgeEvent(subscription.getTopic(), partitionId)
                .subscriptionName(subscription.getName())
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
        LOGGER.error(LOG_MESSAGE_PREFIX + "Unhandled exception during handling of event {}.{}", this, event, resolution, e);
    }

    @Override
    public String getTopicName()
    {
        return subscription.getTopic();
    }

    @Override
    public String toString()
    {
        return "TopicSubscriber[topic=" + subscription.getTopic() + ", partition=" + partitionId + ", name=" + subscription.getName() + ", subscriberKey=" + subscriberKey + "]";
    }

}
