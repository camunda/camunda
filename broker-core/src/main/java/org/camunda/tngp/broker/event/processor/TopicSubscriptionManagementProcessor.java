package org.camunda.tngp.broker.event.processor;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.event.TopicSubscriptionNames;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
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
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.util.DeferredCommandContext;

public class TopicSubscriptionManagementProcessor implements StreamProcessor, EventProcessor
{

    protected static final int MAXIMUM_SUBSCRIPTION_NAME_LENGTH = 32;

    protected final SnapshotSupport snapshotResource;

    protected final int streamId;
    protected final ServiceName<LogStream> streamServiceName;

    protected final SubscriptionRegistry subscriptionRegistry = new SubscriptionRegistry();
    protected long nextSubscriptionId = 0L;

    protected final TopicSubscriptionEvent subscriptionEvent = new TopicSubscriptionEvent();
    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();
    protected long eventKey;

    protected final CommandResponseWriter responseWriter;
    protected final Supplier<SubscribedEventWriter> eventWriterFactory;
    protected final ServiceStartContext serviceContext;
    protected DeferredCommandContext cmdContext;
    protected final Bytes2LongHashIndex ackIndex;

    public TopicSubscriptionManagementProcessor(
            int streamId,
            ServiceName<LogStream> streamServiceName,
            IndexStore ackIndexStore,
            CommandResponseWriter responseWriter,
            Supplier<SubscribedEventWriter> eventWriterFactory,
            ServiceStartContext serviceContext)
    {
        this.streamId = streamId;
        this.streamServiceName = streamServiceName;
        this.responseWriter = responseWriter;
        this.eventWriterFactory = eventWriterFactory;
        this.serviceContext = serviceContext;
        this.ackIndex = new Bytes2LongHashIndex(ackIndexStore, Short.MAX_VALUE, 256, MAXIMUM_SUBSCRIPTION_NAME_LENGTH);
        this.snapshotResource = new HashIndexSnapshotSupport<>(ackIndex, ackIndexStore);
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.cmdContext = context.getStreamProcessorCmdQueue();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return snapshotResource;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventKey = event.getLongKey();

        metadata.reset();
        metadata.wrap(event.getMetadata(), event.getMetadataOffset(), event.getMetadataLength());

        subscriptionEvent.reset();
        subscriptionEvent.wrap(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());

        if (subscriptionEvent.getEvent() == TopicSubscriptionEventType.ACKNOWLEDGE)
        {
            return this;
        }
        else
        {
            return null;
        }

    }

    public static MetadataFilter filter()
    {
        return (m) -> EventType.SUBSCRIPTION_EVENT == m.getEventType();
    }

    @Override
    public void processEvent()
    {
    }

    @Override
    public long writeEvent(LogStreamWriter writer)
    {
        metadata.protocolVersion(Constants.PROTOCOL_VERSION);

        return writer
            .key(eventKey)
            .metadataWriter(metadata)
            .valueWriter(subscriptionEvent)
            .tryWrite();
    }

    @Override
    public boolean executeSideEffects()
    {
        subscriptionEvent.event(TopicSubscriptionEventType.ACKNOWLEDGED);
        return responseWriter
                .brokerEventMetadata(metadata)
                .eventWriter(subscriptionEvent)
                .longKey(eventKey)
                .topicId(streamId)
                .tryWriteResponse();
    }

    @Override
    public void updateState()
    {
        putAck(subscriptionEvent.getSubscriptionName(), subscriptionEvent.getAckPosition());

        final TopicSubscriptionProcessor subscriptionProcessor = subscriptionRegistry.getProcessorByName(subscriptionEvent.getSubscriptionName());

        if (subscriptionProcessor != null)
        {
            subscriptionProcessor.onAck(subscriptionEvent.getAckPosition());
        }
    }

    public void putAck(DirectBuffer subscriptionName, long ackPosition)
    {
        ackIndex.put(subscriptionName, 0, subscriptionName.capacity(), ackPosition);
    }

    protected long determineResumePosition(DirectBuffer subscriptionName, long startPosition)
    {
        final long lastAckedPosition = ackIndex.get(subscriptionName, 0, subscriptionName.capacity(), -1L);

        if (lastAckedPosition >= 0)
        {
            return lastAckedPosition + 1;
        }
        else
        {
            return startPosition;
        }
    }

    public CompletableFuture<Void> openSubscriptionAsync(TopicSubscription subscription)
    {
        final DirectBuffer nameBuffer = subscription.getName();

        if (nameBuffer.capacity() > MAXIMUM_SUBSCRIPTION_NAME_LENGTH)
        {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Subscription name must be " + MAXIMUM_SUBSCRIPTION_NAME_LENGTH + " characters or shorter."));
            return future;
        }

        final UnsafeBuffer subscriptionName = new UnsafeBuffer(new byte[nameBuffer.capacity()]);
        subscriptionName.putBytes(0, nameBuffer, 0, nameBuffer.capacity());
        final int topicId = subscription.getTopicId();
        final int channelId = subscription.getChannelId();
        final long startPosition = subscription.getStartPosition();
        final int prefetchCapacity = subscription.getPrefetchCapacity();

        return cmdContext.runAsync((future) ->
        {
            final long resumePosition = determineResumePosition(subscriptionName, startPosition);

            final long subscriptionId = nextSubscriptionId++;
            subscription.setId(subscriptionId);

            final TopicSubscriptionProcessor streamProcessor = new TopicSubscriptionProcessor(
                    channelId,
                    topicId,
                    subscriptionId,
                    resumePosition,
                    subscriptionName,
                    prefetchCapacity,
                    eventWriterFactory.get());

            createStreamProcessorServiceAsync(
                    streamServiceName,
                    TopicSubscriptionNames.subscriptionPushServiceName(streamServiceName.getName(), streamProcessor.getNameAsString()),
                    StreamProcessorIds.TOPIC_SUBSCRIPTION_PROCESSOR_ID,
                    streamProcessor,
                    TopicSubscriptionProcessor.eventFilter())
                .thenAccept((v) -> subscriptionRegistry.addSubscription(streamProcessor))
                .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
        });
    }

    public CompletableFuture<Void> closeSubscriptionAsync(long subscriptionId)
    {
        return cmdContext.runAsync((future) ->
        {
            final TopicSubscriptionProcessor processor = subscriptionRegistry.removeProcessorById(subscriptionId);

            if (processor != null)
            {
                final ServiceName<StreamProcessorController> subscriptionProcessorName =
                        TopicSubscriptionNames.subscriptionPushServiceName(streamServiceName.getName(), processor.getNameAsString());

                removeStreamProcessorServiceAsync(subscriptionProcessorName)
                    .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
            }
            else
            {
                future.completeExceptionally(new RuntimeException("Subscription with id " + subscriptionId + " is not open"));
            }
        });
    }


    protected CompletableFuture<Void> createStreamProcessorServiceAsync(
            ServiceName<LogStream> logStreamName,
            ServiceName<StreamProcessorController> processorName,
            int processorId,
            StreamProcessor streamProcessor,
            MetadataFilter eventFilter)
    {
        final StreamProcessorService streamProcessorService = new StreamProcessorService(
                processorName.getName(),
                processorId,
                streamProcessor)
            .eventFilter(eventFilter);

        return serviceContext.createService(processorName, streamProcessorService)
            .dependency(logStreamName, streamProcessorService.getSourceStreamInjector())
            .dependency(logStreamName, streamProcessorService.getTargetStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(AGENT_RUNNER_SERVICE, streamProcessorService.getAgentRunnerInjector())
            .install();
    }

    protected CompletableFuture<Void> removeStreamProcessorServiceAsync(ServiceName<StreamProcessorController> processorName)
    {
        return serviceContext.removeService(processorName);
    }

}
