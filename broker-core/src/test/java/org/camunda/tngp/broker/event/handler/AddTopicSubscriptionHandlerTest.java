package org.camunda.tngp.broker.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.event.processor.TopicSubscription;
import org.camunda.tngp.broker.event.processor.TopicSubscriptionManager;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageResponseWriter;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.test.util.FluentMock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AddTopicSubscriptionHandlerTest
{

    protected FuturePool futurePool;

    @Mock
    protected TopicSubscriptionManager subscriptionManager;

    @FluentMock
    protected ControlMessageResponseWriter responseWriter;

    @FluentMock
    protected ErrorResponseWriter errorWriter;

    protected ArgumentCaptor<TopicSubscription> subscriptionCaptor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        futurePool = new FuturePool();
        when(subscriptionManager.addSubscription(any())).thenAnswer((invocation) -> futurePool.next());
        subscriptionCaptor = ArgumentCaptor.forClass(TopicSubscription.class);
    }

    @Test
    public void shouldAddSubscription()
    {
        // given
        final AddTopicSubscriptionHandler handler = new AddTopicSubscriptionHandler(
                subscriptionManager,
                responseWriter,
                errorWriter);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.reqChannelId(14);

        // when
        handler.handle(encode(new TopicSubscription().setTopicId(3)), metadata);

        // then
        verify(subscriptionManager).addSubscription(subscriptionCaptor.capture());

        final TopicSubscription subscription = subscriptionCaptor.getValue();
        assertThat(subscription.getTopicId()).isEqualTo(3);
        assertThat(subscription.getChannelId()).isEqualTo(14);

        verifyZeroInteractions(responseWriter, errorWriter);
    }

    @Test
    public void shouldWriteResponseOnSuccess()
    {
        // given
        final AddTopicSubscriptionHandler handler = new AddTopicSubscriptionHandler(
                subscriptionManager,
                responseWriter,
                errorWriter);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.reqChannelId(14);

        handler.handle(encode(new TopicSubscription().setTopicId(3)), metadata);

        // when
        futurePool.at(0).complete(null);

        // then
        verify(responseWriter).brokerEventMetadata(metadata);
        verify(responseWriter).dataWriter(subscriptionCaptor.capture());
        verify(responseWriter).tryWriteResponse();
        verify(errorWriter, never()).tryWriteResponse();
        verify(errorWriter, never()).tryWriteResponseOrLogFailure();

        final TopicSubscription subscription = subscriptionCaptor.getValue();
        assertThat(subscription.getTopicId()).isEqualTo(3);
    }


    @Test
    public void shouldWriteErrorOnFailure()
    {
        // given
        final AddTopicSubscriptionHandler handler = new AddTopicSubscriptionHandler(
                subscriptionManager,
                responseWriter,
                errorWriter);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.reqChannelId(14);

        final DirectBuffer request = encode(new TopicSubscription().setTopicId(3));
        handler.handle(request, metadata);

        // when
        futurePool.at(0).completeExceptionally(new RuntimeException("foo"));

        // then
        verify(errorWriter).metadata(metadata);
        verify(errorWriter).failedRequest(request, 0, request.capacity());
        verify(errorWriter).errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE);
        verify(errorWriter).tryWriteResponseOrLogFailure();
        verify(responseWriter, never()).tryWriteResponse();

    }

    protected static final DirectBuffer encode(UnpackedObject obj)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[obj.getLength()]);
        obj.write(buffer, 0);
        return buffer;
    }
}
