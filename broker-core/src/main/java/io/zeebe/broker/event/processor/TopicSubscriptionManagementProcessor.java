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

import java.util.Iterator;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.clientapi.SubscribedRecordWriter;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class TopicSubscriptionManagementProcessor implements StreamProcessor
{

    protected static final int MAXIMUM_SUBSCRIPTION_NAME_LENGTH = 32;

    protected final SnapshotSupport snapshotResource;

    protected LogStream logStream;
    protected int logStreamPartitionId;

    protected final Partition partition;
    protected final ServiceName<Partition> partitionServiceName;

    protected final SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();

    protected final ErrorResponseWriter errorWriter;
    protected final CommandResponseWriter responseWriter;
    protected final Supplier<SubscribedRecordWriter> eventWriterFactory;
    protected final StreamProcessorServiceFactory streamProcessorServiceFactory;
    protected final ServiceContainer serviceContext;
    protected final Bytes2LongZbMap ackMap;

    private ActorControl actor;

    protected final AckProcessor ackProcessor = new AckProcessor();
    protected final SubscribeProcessor subscribeProcessor = new SubscribeProcessor(MAXIMUM_SUBSCRIPTION_NAME_LENGTH, this);
    protected final SubscribedProcessor subscribedProcessor = new SubscribedProcessor();

    protected final RecordMetadata metadata = new RecordMetadata();
    protected final TopicSubscriptionEvent subscriptionEvent = new TopicSubscriptionEvent();
    protected final TopicSubscriberEvent subscriberEvent = new TopicSubscriberEvent();
    protected LoggedEvent currentEvent;


    public TopicSubscriptionManagementProcessor(Partition partition,
            ServiceName<Partition> partitionServiceName,
            CommandResponseWriter responseWriter,
            ErrorResponseWriter errorWriter,
            Supplier<SubscribedRecordWriter> eventWriterFactory,
            StreamProcessorServiceFactory streamProcessorServiceFactory,
            ServiceContainer serviceContainer)
    {
        this.partition = partition;
        this.partitionServiceName = partitionServiceName;
        this.responseWriter = responseWriter;
        this.errorWriter = errorWriter;
        this.eventWriterFactory = eventWriterFactory;
        this.ackMap = new Bytes2LongZbMap(MAXIMUM_SUBSCRIPTION_NAME_LENGTH);
        this.snapshotResource = new ZbMapSnapshotSupport<>(ackMap);
        this.serviceContext = serviceContainer;
        this.streamProcessorServiceFactory = streamProcessorServiceFactory;
    }

    public Supplier<SubscribedRecordWriter> getEventWriterFactory()
    {
        return eventWriterFactory;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.actor = context.getActorControl();

        final LogStream logStream = context.getLogStream();
        this.logStreamPartitionId = logStream.getPartitionId();

        this.logStream = logStream;
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

    public LogStream getLogStream()
    {
        return logStream;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {

        metadata.reset();
        event.readMetadata(metadata);
        currentEvent = event;

        if (metadata.getValueType() == ValueType.SUBSCRIPTION)
        {
            return onSubscriptionEvent(event);
        }
        else if (metadata.getValueType() == ValueType.SUBSCRIBER)
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

        if (metadata.getIntent() == Intent.SUBSCRIBE)
        {
            subscribeProcessor.wrap(currentEvent, metadata, subscriberEvent);
            return subscribeProcessor;
        }
        else if (metadata.getIntent() == Intent.SUBSCRIBED)
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

        if (metadata.getIntent() == Intent.ACKNOWLEDGE)
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

    public ActorFuture<Void> closePushProcessorAsync(long subscriberKey)
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
        actor.call(() ->
        {
            final TopicSubscriptionPushProcessor processor = subscriptionRegistry.removeProcessorByKey(subscriberKey);

            if (processor != null)
            {
                final ActorFuture<Void> closeFuture = closePushProcessor(processor);
                actor.runOnCompletion(closeFuture, (aVoid, throwable) ->
                {
                    if (throwable == null)
                    {
                        future.complete(null);
                    }
                    else
                    {
                        future.completeExceptionally(throwable);
                    }
                });
            }
            else
            {
                future.complete(null);
            }
        });
        return future;
    }

    protected ActorFuture<Void> closePushProcessor(TopicSubscriptionPushProcessor processor)
    {
        final ServiceName<StreamProcessorService> pushProcessorServiceName = LogStreamServiceNames.streamProcessorService(logStream.getLogName(), pushProcessorName(processor));
        return serviceContext.removeService(pushProcessorServiceName);
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

    public ActorFuture<StreamProcessorService> openPushProcessorAsync(final TopicSubscriptionPushProcessor processor)
    {
        return streamProcessorServiceFactory.createService(partition, partitionServiceName)
                .processor(processor)
                .processorId(StreamProcessorIds.TOPIC_SUBSCRIPTION_PUSH_PROCESSOR_ID)
                .processorName(pushProcessorName(processor))
                .eventFilter(TopicSubscriptionPushProcessor.eventFilter())
                .additionalDependencies(partitionServiceName)
                .readOnly(true)
                .build();
    }


    public boolean writeRequestResponseError(RecordMetadata metadata, String error)
    {
        return errorWriter
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorMessage(error)
            .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
    }

    public void registerPushProcessor(TopicSubscriptionPushProcessor processor)
    {
        subscriptionRegistry.addSubscription(processor);
    }

    public void onClientChannelCloseAsync(int channelId)
    {
        actor.call(() ->
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
        return (m) -> ValueType.SUBSCRIPTION == m.getValueType() || ValueType.SUBSCRIBER == m.getValueType();
    }

    protected class AckProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            metadata
                .recordType(RecordType.EVENT)
                .valueType(ValueType.SUBSCRIPTION)
                .intent(Intent.ACKNOWLEDGED)
                .protocolVersion(Protocol.PROTOCOL_VERSION);

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
                    .partitionId(logStreamPartitionId)
                    .valueWriter(subscriptionEvent)
                    .key(currentEvent.getKey())
                    .recordType(RecordType.EVENT)
                    .intent(Intent.ACKNOWLEDGED)
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
                    .partitionId(logStreamPartitionId)
                    .recordType(RecordType.EVENT)
                    .intent(Intent.SUBSCRIBED)
                    .valueWriter(subscriberEvent)
                    .position(currentEvent.getPosition())
                    .key(currentEvent.getKey())
                    .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());

            if (responseWritten)
            {
                Loggers.SERVICES_LOGGER.debug("Topic push processor for partition {} successfully opened. Send response for request {}", logStreamPartitionId, metadata.getRequestId());
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
            subscriptionEvent
                .setName(openedSubscriptionName, 0, openedSubscriptionName.capacity())
                .setAckPosition(subscriberEvent.getStartPosition() - 1);

            metadata
                .recordType(RecordType.COMMAND)
                .valueType(ValueType.SUBSCRIPTION)
                .intent(Intent.ACKNOWLEDGE)
                .requestStreamId(-1)
                .requestId(-1);

            return writer
                    .key(currentEvent.getKey())
                    .metadataWriter(metadata)
                    .valueWriter(subscriptionEvent)
                    .tryWrite();
        }
    }

    private static String pushProcessorName(final TopicSubscriptionPushProcessor processor)
    {
        return String.format("topic-push.%s", processor.getNameAsString());
    }

}
