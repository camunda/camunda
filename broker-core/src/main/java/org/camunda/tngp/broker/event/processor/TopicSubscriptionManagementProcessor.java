package org.camunda.tngp.broker.event.processor;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.event.TopicSubscriptionServiceNames;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.util.DeferredCommandContext;

public class TopicSubscriptionManagementProcessor implements StreamProcessor
{

    protected static final int MAXIMUM_SUBSCRIPTION_NAME_LENGTH = 32;

    protected final SnapshotSupport snapshotResource;

    protected int streamId;
    protected final ServiceName<LogStream> streamServiceName;

    protected final SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();

    protected final ErrorResponseWriter errorWriter;
    protected final CommandResponseWriter responseWriter;
    protected final Supplier<SubscribedEventWriter> eventWriterFactory;
    protected final ServiceStartContext serviceContext;
    protected final Bytes2LongHashIndex ackIndex;

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
            IndexStore ackIndexStore,
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
        this.ackIndex = new Bytes2LongHashIndex(ackIndexStore, Short.MAX_VALUE, 256, MAXIMUM_SUBSCRIPTION_NAME_LENGTH);
        this.snapshotResource = new HashIndexSnapshotSupport<>(ackIndex, ackIndexStore);
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.cmdContext = context.getStreamProcessorCmdQueue();
        this.streamId = context.getSourceStream().getId();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return snapshotResource;
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

        if (subscriberEvent.getEventType() == TopicSubscriberEventType.SUBSCRIBE)
        {
            subscribeProcessor.wrap(currentEvent, metadata, subscriberEvent);
            return subscribeProcessor;
        }
        else if (subscriberEvent.getEventType() == TopicSubscriberEventType.SUBSCRIBED)
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

        if (subscriptionEvent.getEventType() == TopicSubscriptionEventType.ACKNOWLEDGE)
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
        ackIndex.put(subscriptionName, 0, subscriptionName.capacity(), ackPosition);
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
        final long lastAckedPosition = ackIndex.get(subscriptionName, 0, subscriptionName.capacity(), -1L);

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
            .dependency(AGENT_RUNNER_SERVICE, streamProcessorService.getAgentRunnerInjector())
            .install()
            .thenApply((v) -> processor);
    }

    public boolean writeRequestResponseError(BrokerEventMetadata metadata, LoggedEvent event, String error)
    {
        return errorWriter
            .metadata(metadata)
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorMessage(error)
            .failedRequest(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
            .tryWriteResponse();
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
            subscriptionEvent.setEventType(TopicSubscriptionEventType.ACKNOWLEDGED);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            metadata.protocolVersion(Constants.PROTOCOL_VERSION);

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

            if (metadata.getReqRequestId() >= 0)
            {
                return responseWriter
                        .brokerEventMetadata(metadata)
                        .eventWriter(subscriptionEvent)
                        .key(currentEvent.getKey())
                        .topicId(streamId)
                        .tryWriteResponse();
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

            final boolean responseWritten = responseWriter.brokerEventMetadata(metadata)
                    .eventWriter(subscriberEvent)
                    .key(currentEvent.getKey())
                    .topicId(streamId)
                    .tryWriteResponse();

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
            subscriptionEvent.setEventType(TopicSubscriptionEventType.ACKNOWLEDGE)
                .setName(openedSubscriptionName, 0, openedSubscriptionName.capacity())
                .setAckPosition(subscriberEvent.getStartPosition() - 1);

            metadata.eventType(EventType.SUBSCRIPTION_EVENT)
                .reqChannelId(-1)
                .reqConnectionId(-1)
                .reqRequestId(-1);

            return writer
                    .key(currentEvent.getKey())
                    .metadataWriter(metadata)
                    .valueWriter(subscriptionEvent)
                    .tryWrite();

        }
    }

}
