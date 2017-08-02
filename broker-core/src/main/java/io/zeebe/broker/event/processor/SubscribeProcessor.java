/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.event.processor;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.zeebe.protocol.Protocol;
import org.agrona.DirectBuffer;

import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;

public class SubscribeProcessor implements EventProcessor
{
    protected final int maximumNameLength;
    protected final TopicSubscriptionManagementProcessor manager;

    protected LoggedEvent event;
    protected BrokerEventMetadata metadata;
    protected TopicSubscriberEvent subscriberEvent;

    protected EventProcessor state;
    protected final RequestFailureProcessor failedRequestState = new RequestFailureProcessor();
    protected final CreateSubscriptionServiceProcessor createProcessorState = new CreateSubscriptionServiceProcessor();
    protected final AwaitSubscriptionServiceProcessor awaitProcessorState = new AwaitSubscriptionServiceProcessor();
    protected final SubscriptionServiceSuccessProcessor successState = new SubscriptionServiceSuccessProcessor();

    public SubscribeProcessor(
            int maximumNameLength,
            TopicSubscriptionManagementProcessor manager)
    {
        this.maximumNameLength = maximumNameLength;
        this.manager = manager;
    }

    public void wrap(LoggedEvent event, BrokerEventMetadata metadata, TopicSubscriberEvent subscriberEvent)
    {
        this.event = event;
        this.metadata = metadata;
        this.subscriberEvent = subscriberEvent;
    }

    @Override
    public void processEvent()
    {
        final DirectBuffer subscriptionName = subscriberEvent.getName();

        if (subscriptionName.capacity() > maximumNameLength)
        {
            failedRequestState.wrapError("Cannot open topic subscription " + subscriberEvent.getNameAsString() +
                    ". Subscription name must be " + maximumNameLength + " characters or shorter.");
            state = failedRequestState;
            return;
        }
        else
        {
            state = createProcessorState;
        }
    }

    @Override
    public long writeEvent(LogStreamWriter writer)
    {
        return state.writeEvent(writer);
    }

    @Override
    public boolean executeSideEffects()
    {
        return state.executeSideEffects();
    }

    protected class RequestFailureProcessor implements EventProcessor
    {
        protected String error;

        public void wrapError(String error)
        {
            this.error = error;
        }

        @Override
        public void processEvent()
        {
        }

        @Override
        public boolean executeSideEffects()
        {
            return manager.writeRequestResponseError(metadata, event, error);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            // in the future, we can write a SUBSCRIBE_FAILED event here,
            // but at the moment that would make no difference for the user
            return 0L;
        }

    }

    protected class CreateSubscriptionServiceProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
        }

        @Override
        public boolean executeSideEffects()
        {
            final DirectBuffer subscriptionName = subscriberEvent.getName();
            final long resumePosition = manager.determineResumePosition(
                    subscriptionName,
                    subscriberEvent.getStartPosition(),
                    subscriberEvent.getForceStart());

            final CompletableFuture<TopicSubscriptionPushProcessor> processorFuture = manager.openPushProcessorAsync(
                    metadata.getRequestStreamId(),
                    event.getKey(),
                    resumePosition,
                    subscriptionName,
                    subscriberEvent.getPrefetchCapacity());

            awaitProcessorState.wrap(processorFuture);
            state = awaitProcessorState;

            return false;
        }
    }

    protected class AwaitSubscriptionServiceProcessor implements EventProcessor
    {
        protected CompletableFuture<TopicSubscriptionPushProcessor> processorFuture;

        public void wrap(CompletableFuture<TopicSubscriptionPushProcessor> processorFuture)
        {
            this.processorFuture = processorFuture;
        }

        @Override
        public void processEvent()
        {
        }

        @Override
        public boolean executeSideEffects()
        {
            if (!processorFuture.isDone())
            {
                return false;
            }
            else
            {
                try
                {
                    final TopicSubscriptionPushProcessor processor = processorFuture.get();
                    successState.wrap(processor);
                    state = successState;

                    return false;
                }
                catch (CancellationException | ExecutionException e)
                {
                    final String errorMessage = e.getMessage();

                    failedRequestState.wrapError(errorMessage);
                    state = failedRequestState;

                    return false;
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException("Unexpected exception", e);
                }
            }
        }
    }

    protected class SubscriptionServiceSuccessProcessor implements EventProcessor
    {

        protected TopicSubscriptionPushProcessor processor;

        public void wrap(TopicSubscriptionPushProcessor processor)
        {
            this.processor = processor;
        }

        @Override
        public void processEvent()
        {
        }

        @Override
        public boolean executeSideEffects()
        {
            // success response is written on SUBSCRIBED event, as only then it is guaranteed that
            //   the start position is persisted

            manager.registerPushProcessor(processor);
            return true;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            metadata.protocolVersion(Protocol.PROTOCOL_VERSION)
                .raftTermId(manager.getTargetStream().getTerm());

            subscriberEvent
                .setStartPosition(processor.getStartPosition())
                .setState(TopicSubscriberState.SUBSCRIBED);

            return writer
                .metadataWriter(metadata)
                .valueWriter(subscriberEvent)
                .key(event.getKey())
                .tryWrite();
        }
    }
}
