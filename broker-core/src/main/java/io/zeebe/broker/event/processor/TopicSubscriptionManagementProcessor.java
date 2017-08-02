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

import static io.zeebe.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.logstreams.processor.StreamProcessorService;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.clientapi.SubscribedEventWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.DeferredCommandContext;
import org.agrona.DirectBuffer;

public class TopicSubscriptionManagementProcessor implements StreamProcessor
{

    protected static final int MAXIMUM_SUBSCRIPTION_NAME_LENGTH = 32;

    protected final SnapshotSupport snapshotResource;

    protected LogStream targetStream;
    protected DirectBuffer logStreamTopicName;
    protected int logStreamPartitionId;
    protected final ServiceName<LogStream> streamServiceName;

    protected final SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();

    protected final ErrorResponseWriter errorWriter;
    protected final CommandResponseWriter responseWriter;
    protected final Supplier<SubscribedEventWriter> eventWriterFactory;
    protected final ServiceStartContext serviceContext;
    protected final Bytes2LongZbMap ackMap;

    protected DeferredCommandContext cmdContext;

    protected final AckProcessor ackProcessor = new AckProcessor();
    protected final SubscribeProcessor subscribeProcessor = new SubscribeProcessor(MAXIMUM_SUBSCRIPTION_NAME_LENGTH, this);
    protected final SubscribedProcessor subscribedProcessor = new SubscribedProcessor();

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();
    protected final TopicSubscriptionEvent subscriptionEvent = new TopicSubscriptionEvent();
    protected final TopicSubscriberEvent subscriberEvent = new TopicSubscriberEvent();
    protected LoggedEvent currentEvent;

    public TopicSubscriptionManagementProcessor(
            ServiceName<LogStream> streamServiceName,
            CommandResponseWriter responseWriter,
            ErrorResponseWriter errorWriter,
            Supplier<SubscribedEventWriter> eventWriterFactory,
            ServiceStartContext serviceContext)
    {
        this.streamServiceName = streamServiceName;
        this.responseWriter = responseWriter;
        this.errorWriter = errorWriter;
        this.eventWriterFactory = eventWriterFactory;
        this.serviceContext = serviceContext;
        this.ackMap = new Bytes2LongZbMap(MAXIMUM_SUBSCRIPTION_NAME_LENGTH);
        this.snapshotResource = new ZbMapSnapshotSupport<>(ackMap);
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.cmdContext = context.getStreamProcessorCmdQueue();

        final LogStream sourceStream = context.getSourceStream();
        this.logStreamTopicName = sourceStream.getTopicName();
        this.logStreamPartitionId = sourceStream.getPartitionId();

        targetStream = context.getTargetStream();
    }

    @Override
    public void onClose()
    {
        ackMap.close();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return snapshotResource;
    }

    public LogStream getTargetStream()
    {
        return targetStream;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {

        metadata.reset();
        event.readMetadata(metadata);
        currentEvent = event;

        if (metadata.getEventType() == EventType.SUBSCRIPTION_EVENT)
        {
            return onSubscriptionEvent(event);
        }
        else if (metadata.getEventType() == EventType.SUBSCRIBER_EVENT)
        {
            return onSubscriberEvent(event);
        }
        else
        {
            return null;
        }
    }

    protected EventProcessor onSubscriberEvent(LoggedEvent event)
    {
        subscriberEvent.reset();
        subscriberEvent.wrap(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());

        if (subscriberEvent.getState() == TopicSubscriberState.SUBSCRIBE)
        {
            subscribeProcessor.wrap(currentEvent, metadata, subscriberEvent);
            return subscribeProcessor;
        }
        else if (subscriberEvent.getState() == TopicSubscriberState.SUBSCRIBED)
        {
            return subscribedProcessor;
        }
        else
        {
            return null;
        }
    }

    protected EventProcessor onSubscriptionEvent(LoggedEvent event)
    {
        subscriptionEvent.reset();
        subscriptionEvent.wrap(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());

        if (subscriptionEvent.getState() == TopicSubscriptionState.ACKNOWLEDGE)
        {
            return ackProcessor;
        }
        else
        {
            return null;
        }
    }

    protected void putAck(DirectBuffer subscriptionName, long ackPosition)
    {
        ackMap.put(subscriptionName, 0, subscriptionName.capacity(), ackPosition);
    }

    public CompletableFuture<Void> closePushProcessorAsync(long subscriberKey)
    {
        return cmdContext.runAsync((future) ->
        {
            final TopicSubscriptionPushProcessor processor = subscriptionRegistry.removeProcessorByKey(subscriberKey);

            if (processor != null)
            {
                closePushProcessor(processor)
                    .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
            }
            else
            {
                future.complete(null);
            }
        });
    }

    protected CompletableFuture<Void> closePushProcessor(TopicSubscriptionPushProcessor processor)
    {
        final ServiceName<StreamProcessorController> subscriptionProcessorName =
                TopicSubscriptionServiceNames.subscriptionPushServiceName(streamServiceName.getName(), processor.getNameAsString());

        return serviceContext.removeService(subscriptionProcessorName);
    }


    public long determineResumePosition(DirectBuffer subscriptionName, long startPosition, boolean forceStart)
    {
        final long lastAckedPosition = ackMap.get(subscriptionName, 0, subscriptionName.capacity(), -1L);

        if (forceStart)
        {
            return startPosition;
        }
        else
        {
            if (lastAckedPosition >= 0)
            {
                return lastAckedPosition + 1;
            }
            else
            {
                return startPosition;
            }
        }
    }

    public CompletableFuture<TopicSubscriptionPushProcessor> openPushProcessorAsync(
            int clientChannelId,
            long subscriberKey,
            long resumePosition,
            DirectBuffer subscriptionName,
            int prefetchCapacity)
    {
        final TopicSubscriptionPushProcessor processor = new TopicSubscriptionPushProcessor(
                clientChannelId,
                subscriberKey,
                resumePosition,
                subscriptionName,
                prefetchCapacity,
                eventWriterFactory.get());

        final ServiceName<StreamProcessorController> serviceName = TopicSubscriptionServiceNames.subscriptionPushServiceName(streamServiceName.getName(), processor.getNameAsString());

        final StreamProcessorService streamProcessorService = new StreamProcessorService(
                serviceName.getName(),
                StreamProcessorIds.TOPIC_SUBSCRIPTION_PUSH_PROCESSOR_ID,
                processor)
            .eventFilter(TopicSubscriptionPushProcessor.eventFilter())
            .readOnly(true);

        return serviceContext.createService(serviceName, streamProcessorService)
            .dependency(streamServiceName, streamProcessorService.getSourceStreamInjector())
            .dependency(streamServiceName, streamProcessorService.getTargetStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, streamProcessorService.getActorSchedulerInjector())
            .install()
            .thenApply((v) -> processor);
    }

    public boolean writeRequestResponseError(BrokerEventMetadata metadata, LoggedEvent event, String error)
    {
        return errorWriter
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorMessage(error)
            .failedRequest(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
            .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
    }

    public void registerPushProcessor(TopicSubscriptionPushProcessor processor)
    {
        subscriptionRegistry.addSubscription(processor);
    }

    public void onClientChannelCloseAsync(int channelId)
    {
        cmdContext.runAsync(() ->
        {
            final Iterator<TopicSubscriptionPushProcessor> subscriptionsIt = subscriptionRegistry.iterateSubscriptions();

            while (subscriptionsIt.hasNext())
            {
                final TopicSubscriptionPushProcessor processor = subscriptionsIt.next();
                if (processor.getChannelId() == channelId)
                {
                    subscriptionsIt.remove();
                    closePushProcessor(processor);
                }
            }
        });
    }


    public static MetadataFilter filter()
    {
        return (m) -> EventType.SUBSCRIPTION_EVENT == m.getEventType() || EventType.SUBSCRIBER_EVENT == m.getEventType();
    }


    protected class AckProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            subscriptionEvent.setState(TopicSubscriptionState.ACKNOWLEDGED);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            metadata.protocolVersion(Protocol.PROTOCOL_VERSION)
                .raftTermId(targetStream.getTerm());

            return writer
                .key(currentEvent.getKey())
                .metadataWriter(metadata)
                .valueWriter(subscriptionEvent)
                .tryWrite();
        }

        @Override
        public boolean executeSideEffects()
        {
            final TopicSubscriptionPushProcessor subscriptionProcessor = subscriptionRegistry.getProcessorByName(subscriptionEvent.getName());

            if (subscriptionProcessor != null)
            {
                subscriptionProcessor.onAck(subscriptionEvent.getAckPosition());
            }

            if (metadata.getRequestId() >= 0)
            {
                return responseWriter
                        .topicName(logStreamTopicName)
                        .partitionId(logStreamPartitionId)
                        .eventWriter(subscriptionEvent)
                        .key(currentEvent.getKey())
                        .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
            }
            else
            {
                return true;
            }
        }

        @Override
        public void updateState()
        {
            putAck(subscriptionEvent.getName(), subscriptionEvent.getAckPosition());
        }
    }

    protected class SubscribedProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
        }

        @Override
        public boolean executeSideEffects()
        {

            final boolean responseWritten = responseWriter
                    .topicName(logStreamTopicName)
                    .partitionId(logStreamPartitionId)
                    .eventWriter(subscriberEvent)
                    .position(currentEvent.getPosition())
                    .key(currentEvent.getKey())
                    .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());

            if (responseWritten)
            {
                final TopicSubscriptionPushProcessor pushProcessor = subscriptionRegistry.getProcessorByName(subscriberEvent.getName());
                pushProcessor.enable();
            }

            return responseWritten;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            final DirectBuffer openedSubscriptionName = subscriberEvent.getName();

            subscriptionEvent.reset();
            subscriptionEvent.setState(TopicSubscriptionState.ACKNOWLEDGE)
                .setName(openedSubscriptionName, 0, openedSubscriptionName.capacity())
                .setAckPosition(subscriberEvent.getStartPosition() - 1);

            metadata.eventType(EventType.SUBSCRIPTION_EVENT)
                .requestStreamId(-1)
                .requestId(-1)
                .raftTermId(targetStream.getTerm());

            return writer
                    .key(currentEvent.getKey())
                    .metadataWriter(metadata)
                    .valueWriter(subscriptionEvent)
                    .tryWrite();

        }
    }

}
