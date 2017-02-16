package org.camunda.tngp.broker.event.processor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.event.TopicSubscriptionNames;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceName;

public class TopicSubscriptionManager implements Agent
{
    protected static final String NAME = "topic.subscription.manager";

    protected long nextSubscriptionId = 0;

    protected final StreamProcessorManager streamProcessorManager;
    protected final AsyncContext asyncContext;
    protected final Long2ObjectHashMap<TopicSubscriptionProcessor> subscriptionProcessors;
    protected final Supplier<SubscribedEventWriter> eventWriterFactory;

    public TopicSubscriptionManager(StreamProcessorManager streamProcessorManager, AsyncContext asyncContext, Supplier<SubscribedEventWriter> eventWriterFactory)
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
                    eventWriterFactory.get());

            streamProcessorManager.createStreamProcessorService(
                    logStreamServiceName,
                    TopicSubscriptionNames.subscriptionServiceName(logStreamServiceName.getName(), subscriptionId),
                    (int) subscriptionId, // TODO: should this be a constant value as in tasksubscriptionmanager
                    streamProcessor
                    )
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
                final ServiceName<LogStream> logStreamServiceName = streamProcessorManager.getServiceName(
                        subscription.getLogStreamId());
                if (logStreamServiceName != null)
                {
                    final ServiceName<StreamProcessorController> subscriptionProcessorName =
                            TopicSubscriptionNames.subscriptionServiceName(logStreamServiceName.getName(), subscriptionId);
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
