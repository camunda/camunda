package org.camunda.tngp.broker.event.processor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.event.TopicSubscriptionNames;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.util.DeferredCommandContext;

public class TopicSubscriptionManager implements Agent
{
    protected static final String NAME = "topic.subscription.manager";

    protected long nextSubscriptionId = 0;

    protected final StreamProcessorManager streamProcessorManager;
    protected final DeferredCommandContext asyncContext;
    protected final Long2ObjectHashMap<TopicSubscriptionProcessor> subscriptionProcessors;
    protected final Supplier<SubscribedEventWriter> eventWriterFactory;

    public TopicSubscriptionManager(StreamProcessorManager streamProcessorManager, DeferredCommandContext asyncContext, Supplier<SubscribedEventWriter> eventWriterFactory)
    {
        this.streamProcessorManager = streamProcessorManager;
        this.asyncContext = asyncContext;
        this.subscriptionProcessors = new Long2ObjectHashMap<>();
        this.eventWriterFactory = eventWriterFactory;
    }

    public CompletableFuture<Void> addSubscription(TopicSubscription subscription)
    {
        Objects.requireNonNull(subscription);
        final int topicId = subscription.getTopicId();
        final int channelId = subscription.getChannelId();
        final long startPosition = subscription.getStartPosition();
        final String name = subscription.getName();

        return asyncContext.runAsync((future) ->
        {
            // it is ok to access the subscription object asynchronously, because this code is guaranteed to execute not more
            // than once in parallel
            final long subscriptionId = nextSubscriptionId++;
            subscription.setId(subscriptionId);

            final ServiceName<LogStream> logStreamServiceName = streamProcessorManager.getServiceName(topicId);
            Objects.requireNonNull(logStreamServiceName);

            final TopicSubscriptionProcessor streamProcessor = new TopicSubscriptionProcessor(
                    channelId,
                    topicId,
                    subscriptionId,
                    startPosition,
                    name,
                    eventWriterFactory.get());

            streamProcessorManager.createStreamProcessorService(
                    logStreamServiceName,
                    TopicSubscriptionNames.subscriptionServiceName(logStreamServiceName.getName(), name),
                    StreamProcessorIds.TOPIC_SUBSCRIPTION_PROCESSOR_ID,
                    streamProcessor,
                    TopicSubscriptionProcessor.eventFilter(),
                    streamProcessor.reprocessingFilter(),
                    streamProcessor)
                .thenAccept((v) -> subscriptionProcessors.put(subscriptionId, streamProcessor))
                .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
        });
    }

    public CompletableFuture<Void> removeSubscription(long subscriptionId)
    {
        return asyncContext.runAsync((future) ->
        {
            final TopicSubscriptionProcessor subscription = subscriptionProcessors.get(subscriptionId);

            boolean removalScheduled = false;
            if (subscription != null)
            {
                final int topicId = subscription.getLogStreamId();
                final ServiceName<LogStream> logStreamServiceName = streamProcessorManager.getServiceName(topicId);
                if (logStreamServiceName != null)
                {

                    final ServiceName<StreamProcessorController> subscriptionProcessorName =
                            TopicSubscriptionNames.subscriptionServiceName(logStreamServiceName.getName(), subscription.getName());

                    streamProcessorManager.removeStreamProcessorService(subscriptionProcessorName)
                        .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
                    removalScheduled = true;
                }
            }

            if (!removalScheduled)
            {
                future.complete(null);
            }
        });
    }

    public CompletableFuture<Void> submitAcknowledgedPosition(long subscriptionId, long acknowledgedPosition)
    {
        return asyncContext.runAsync((future) ->
        {
            final TopicSubscriptionProcessor subscriptionProcessor = subscriptionProcessors.get(subscriptionId);

            if (subscriptionProcessor != null)
            {
                subscriptionProcessor.acknowledgeEventPosition(acknowledgedPosition);
            }
            else
            {
                throw new RuntimeException("Subscription with id " + subscriptionId + " is not open");
            }

            future.complete(null);
        });
    }

    public void resolveAck(long subscriptionId)
    {
        asyncContext.runAsync(() ->
        {
            final TopicSubscriptionProcessor subscriptionProcessor = subscriptionProcessors.get(subscriptionId);
            if (subscriptionProcessor != null)
            {
                subscriptionProcessor.resolveAck();
            }
            else
            {
                // subscription was closed; ignore ack
            }
        });
    }

    public void onStreamAdded(ServiceName<LogStream> logStreamServiceName, LogStream logStream)
    {
        asyncContext.runAsync(() ->
        {
            final TopicSubscriptionAckProcessor ackProcessor = new TopicSubscriptionAckProcessor(this);

            streamProcessorManager.createStreamProcessorService(
                    logStreamServiceName,
                    TopicSubscriptionNames.ackServiceName(logStream.getContext().getLogName()),
                    StreamProcessorIds.TOPIC_SUBSCRIPTION_ACK_PROCESSOR_ID,
                    ackProcessor,
                    TopicSubscriptionAckProcessor.filter(),
                    null,
                    null)
                .thenAccept((s) -> streamProcessorManager.addLogStream(logStreamServiceName, logStream));
        });
    }

    public void onStreamRemoved(LogStream logStream)
    {

        streamProcessorManager.removeLogStream(logStream);

        asyncContext.runAsync(() ->
        {
            final ServiceName<StreamProcessorController> ackService =
                    TopicSubscriptionNames.ackServiceName(logStream.getContext().getLogName());

            streamProcessorManager.removeStreamProcessorService(ackService);
        });
    }

    @Override
    public int doWork() throws Exception
    {
        return asyncContext.doWork();
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

}
