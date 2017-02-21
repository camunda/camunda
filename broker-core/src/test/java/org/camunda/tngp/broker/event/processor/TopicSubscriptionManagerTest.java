package org.camunda.tngp.broker.event.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.event.handler.StaticSupplier.returnInOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.event.TopicSubscriptionNames;
import org.camunda.tngp.broker.event.handler.FuturePool;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TopicSubscriptionManagerTest
{

    protected static final ServiceName<LogStream> EXAMPLE_LOG_STREAM_NAME = ServiceName.newServiceName("foo", LogStream.class);
    protected static final int LOG_STREAM_ID = 2;

    @Mock
    protected StreamProcessorManager streamProcessorManager;

    @Mock
    protected SubscribedEventWriter eventWriter;

    protected ArgumentCaptor<TopicSubscriptionProcessor> processorCaptor;
    protected FuturePool creationFutures;
    protected FuturePool removalFutures;

    protected TopicSubscription subscription;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        processorCaptor = ArgumentCaptor.forClass(TopicSubscriptionProcessor.class);
        creationFutures = new FuturePool();
        removalFutures = new FuturePool();
        when(streamProcessorManager.getServiceName(LOG_STREAM_ID)).thenReturn(EXAMPLE_LOG_STREAM_NAME);
        when(streamProcessorManager.createStreamProcessorService(any(), any(), anyInt(), any(), any())).thenAnswer((invocation) -> creationFutures.next());
        when(streamProcessorManager.removeStreamProcessorService(any())).thenAnswer((invocation) -> removalFutures.next());

        subscription = new TopicSubscription()
                .setChannelId(1)
                .setTopicId(LOG_STREAM_ID);
    }

    @Test
    public void shouldScheduleSubscriptionCreation()
    {

        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        // when
        final CompletableFuture<Void> future = manager.addSubscription(subscription);

        // then
        assertThat(future).isNotNull();
        assertThat(future).isNotDone();
        verifyZeroInteractions(streamProcessorManager, eventWriter);
    }

    @Test
    public void shouldScheduleSubscriptionProcessorCreation() throws Exception
    {
        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        final CompletableFuture<Void> future = manager.addSubscription(subscription);

        // when
        manager.doWork();

        // then
        assertThat(future).isNotDone();
        verify(streamProcessorManager).createStreamProcessorService(same(EXAMPLE_LOG_STREAM_NAME), any(), anyInt(), processorCaptor.capture(), any());

        final TopicSubscriptionProcessor processor = processorCaptor.getValue();
        assertThat(processor.getLogStreamId()).isEqualTo(LOG_STREAM_ID);
        assertThat(processor.getChannelId()).isEqualTo(1);
        assertThat(processor.getChannelWriter()).isEqualTo(eventWriter);
    }

    @Test
    public void shouldCompleteCreation() throws Exception
    {
        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        final CompletableFuture<Void> future = manager.addSubscription(subscription);
        manager.doWork();

        // when
        creationFutures.at(0).complete(null);

        // then
        assertThat(future).isCompleted();
    }


    @Test
    public void shouldFailCreationOnFailedRegistration() throws Exception
    {
        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        final CompletableFuture<Void> future = manager.addSubscription(subscription);
        manager.doWork();

        // when
        creationFutures.at(0).completeExceptionally(new RuntimeException("foo"));

        // then
        assertThat(future).isCompletedExceptionally();
    }

    @Test
    public void shouldScheduleSubscriptionRemoval() throws Exception
    {
        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        manager.addSubscription(subscription);
        manager.doWork();
        creationFutures.at(0).complete(null);

        // when
        final CompletableFuture<Void> future = manager.removeSubscription(subscription.getId());

        // then
        assertThat(future).isNotNull();
        assertThat(future).isNotDone();
        verify(streamProcessorManager, never()).removeStreamProcessorService(any());
    }


    @Test
    public void shouldScheduleSubscriptionServiceRemoval() throws Exception
    {
        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        manager.addSubscription(subscription);
        manager.doWork();
        creationFutures.at(0).complete(null);

        final CompletableFuture<Void> future = manager.removeSubscription(subscription.getId());

        // when
        manager.doWork();

        // then
        assertThat(future).isNotNull();
        assertThat(future).isNotDone();
        verify(streamProcessorManager).removeStreamProcessorService(
                TopicSubscriptionNames.subscriptionServiceName(EXAMPLE_LOG_STREAM_NAME.getName(), subscription.getId()));
    }

    @Test
    public void shouldCompleteRemoval() throws Exception
    {
        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        manager.addSubscription(subscription);
        manager.doWork();
        creationFutures.at(0).complete(null);

        final CompletableFuture<Void> future = manager.removeSubscription(subscription.getId());
        manager.doWork();

        // when
        removalFutures.at(0).complete(null);

        // then
        assertThat(future).isCompleted();
    }

    @Test
    public void shouldFailRemovalOnFailedDeregistration() throws Exception
    {
        // given
        final TopicSubscriptionManager manager = new TopicSubscriptionManager(
                streamProcessorManager,
                new AsyncContext(),
                returnInOrder(eventWriter));

        manager.addSubscription(subscription);
        manager.doWork();
        creationFutures.at(0).complete(null);

        final CompletableFuture<Void> future = manager.removeSubscription(subscription.getId());
        manager.doWork();

        // when
        removalFutures.at(0).completeExceptionally(new RuntimeException("foo"));

        // then
        assertThat(future).isCompletedExceptionally();
    }
}
