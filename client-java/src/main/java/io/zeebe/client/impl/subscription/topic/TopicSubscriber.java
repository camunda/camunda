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
import java.util.function.Function;

import io.zeebe.client.api.events.TopicSubscriptionEvent;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.record.GeneralRecordImpl;
import io.zeebe.client.impl.subscription.*;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class TopicSubscriber extends Subscriber
{
    private static final int MAX_HANDLING_RETRIES = 2;

    private final ZeebeClientImpl client;

    private AtomicBoolean processingFlag = new AtomicBoolean(false);
    private volatile long lastProcessedEventPosition;
    private long lastAcknowledgedPosition;

    private final TopicSubscriptionSpec subscription;

    private final Function<CheckedConsumer<GeneralRecordImpl>, CheckedConsumer<GeneralRecordImpl>> eventHandlerAdapter;

    public TopicSubscriber(
            ZeebeClientImpl client,
            TopicSubscriptionSpec subscription,
            long subscriberKey,
            RemoteAddress eventSource,
            int partitionId,
            SubscriberGroup group,
            SubscriptionManager acquisition)
    {
        super(subscriberKey, partitionId, subscription.getPrefetchCapacity(), eventSource, group, acquisition);
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

    @Override
    public int pollEvents(CheckedConsumer<GeneralRecordImpl> consumer)
    {
        return super.pollEvents(eventHandlerAdapter.apply(consumer));
    }

    protected void logExceptionAndClose(GeneralRecordImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Closing subscription.");
        disable();

        acquisition.closeGroup(group, "Event handling failed");
    }

    protected void logExceptionAndPropagate(GeneralRecordImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Propagating exception to caller.");
        throw new RuntimeException(e);
    }

    protected void logRetry(GeneralRecordImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Retrying.");
    }

    @Override
    protected ActorFuture<Void> requestSubscriptionClose()
    {
        LOGGER.debug("Closing subscriber at partition {}", partitionId);

        return new CloseTopicSubscriptionCommandImpl(client.getCommandManager(), partitionId, subscriberKey)
                .executeAsync();
    }

    @Override
    protected ActorFuture<?> requestEventSourceReplenishment(int eventsProcessed)
    {
        return acknowledgeLastProcessedEvent();
    }

    protected ActorFuture<?> acknowledgeLastProcessedEvent()
    {

        // note: it is important we read lastProcessedEventPosition only once
        //   as it can be changed concurrently by an executor thread
        final long positionToAck = lastProcessedEventPosition;

        if (positionToAck > lastAcknowledgedPosition)
        {
            // TODO: what to do on error here? close the group (but only if it is not already closing)
            final ActorFuture<TopicSubscriptionEvent> future = new AcknowledgeSubscribedEventCommandImpl(client.getCommandManager(), subscription.getTopic(), partitionId)
                .subscriptionName(subscription.getName())
                .ackPosition(positionToAck)
                .executeAsync();

            // record this immediately to avoid repeated requests for the same position
            lastAcknowledgedPosition = positionToAck;

            return future;
        }
        else
        {
            return CompletableActorFuture.<Void>completed(null);
        }
    }

    protected void recordProcessedEvent(GeneralRecordImpl event)
    {
        this.lastProcessedEventPosition = event.getMetadata().getPosition();
    }

    protected void logEventHandlingError(Exception e, GeneralRecordImpl event, String resolution)
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
