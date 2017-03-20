package org.camunda.tngp.broker.event.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.event.handler.FuturePool;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.servicecontainer.ServiceBuilder;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.test.util.FluentMock;
import org.camunda.tngp.util.DeferredCommandContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TopicSubscriptionManagementProcessorTest
{

    protected static final ServiceName<LogStream> EXAMPLE_LOG_STREAM_NAME = ServiceName.newServiceName("foo", LogStream.class);
    protected static final int LOG_STREAM_ID = 2;
    protected static final String SUBSCRIPTION_NAME = "sub";

    @Mock
    protected SubscribedEventWriter eventWriter;

    @Mock
    protected CommandResponseWriter responseWriter;

    @Mock
    protected IndexStore indexStore;

    @Mock
    protected ServiceStartContext serviceContext;

    @FluentMock
    protected ServiceBuilder<Object> serviceBuilder;

    protected FuturePool creationFutures;
    protected FuturePool removalFutures;

    protected TopicSubscription subscription;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        creationFutures = new FuturePool();
        removalFutures = new FuturePool();

        when(serviceContext.createService(any(), any())).thenReturn(serviceBuilder);
        when(serviceBuilder.install()).thenAnswer((invocation) -> creationFutures.next());
        when(serviceContext.removeService(any())).thenAnswer((invocation) -> removalFutures.next());

        subscription = new TopicSubscription()
                .setChannelId(1)
                .setTopicId(LOG_STREAM_ID)
                .setName(SUBSCRIPTION_NAME);
    }

    @Test
    public void shouldFailCompletionWhenServiceCreationFails()
    {
        // given
        final TopicSubscriptionManagementProcessor managementProcessor = new TopicSubscriptionManagementProcessor(
            123,
            EXAMPLE_LOG_STREAM_NAME,
            indexStore,
            responseWriter,
            () -> eventWriter,
            serviceContext);

        final StreamProcessorContext streamProcessorContext = new StreamProcessorContext();
        final DeferredCommandContext commandContext = new DeferredCommandContext();
        streamProcessorContext.setStreamProcessorCmdQueue(commandContext);
        managementProcessor.onOpen(streamProcessorContext);

        final TopicSubscription subscription = new TopicSubscription();
        subscription.setChannelId(123);
        subscription.setName("foo");
        subscription.setTopicId(0);

        final CompletableFuture<Void> openFuture = managementProcessor.openSubscriptionAsync(subscription);
        commandContext.doWork();

        // when
        creationFutures.at(0).completeExceptionally(new RuntimeException("bar"));

        // then
        assertThat(openFuture).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("bar");
    }

    @Test
    public void shouldFailRemovalWhenServiceRemovalFails()
    {
        // given
        final TopicSubscriptionManagementProcessor managementProcessor = new TopicSubscriptionManagementProcessor(
            123,
            EXAMPLE_LOG_STREAM_NAME,
            indexStore,
            responseWriter,
            () -> eventWriter,
            serviceContext);

        final StreamProcessorContext streamProcessorContext = new StreamProcessorContext();
        final DeferredCommandContext commandContext = new DeferredCommandContext();
        streamProcessorContext.setStreamProcessorCmdQueue(commandContext);
        managementProcessor.onOpen(streamProcessorContext);

        final TopicSubscription subscription = new TopicSubscription();
        subscription.setChannelId(123);
        subscription.setName("foo");
        subscription.setTopicId(0);

        managementProcessor.openSubscriptionAsync(subscription);
        commandContext.doWork();
        creationFutures.at(0).complete(null);

        final CompletableFuture<Void> closeFuture = managementProcessor.closeSubscriptionAsync(subscription.getId());
        commandContext.doWork();

        // when
        removalFutures.at(0).completeExceptionally(new RuntimeException("bar"));

        // then
        assertThat(closeFuture).hasFailedWithThrowableThat()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("bar");
    }

}
