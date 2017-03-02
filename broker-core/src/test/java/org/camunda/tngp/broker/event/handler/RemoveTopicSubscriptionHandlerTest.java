package org.camunda.tngp.broker.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.event.processor.CloseSubscriptionRequest;
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

public class RemoveTopicSubscriptionHandlerTest
{

    protected FuturePool futurePool;

    @Mock
    protected TopicSubscriptionManager subscriptionManager;

    @FluentMock
    protected ControlMessageResponseWriter responseWriter;

    @FluentMock
    protected ErrorResponseWriter errorWriter;

    protected ArgumentCaptor<CloseSubscriptionRequest> requestCaptor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        futurePool = new FuturePool();
        when(subscriptionManager.removeSubscription(anyInt())).thenAnswer((invocation) -> futurePool.next());
        requestCaptor = ArgumentCaptor.forClass(CloseSubscriptionRequest.class);
    }

    @Test
    public void shouldRemoveSubscription()
    {
        // given
        final RemoveTopicSubscriptionHandler handler = new RemoveTopicSubscriptionHandler(
                subscriptionManager,
                responseWriter,
                errorWriter);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.reqChannelId(14);
        final DirectBuffer encodedSubscription =
                encode(new CloseSubscriptionRequest().subscriptionId(6));

        // when
        handler.handle(encodedSubscription, metadata);

        // then
        verify(subscriptionManager).removeSubscription(6);
        verifyZeroInteractions(responseWriter, errorWriter);
    }

    @Test
    public void shouldWriteResponseOnSuccess()
    {
        // given
        final RemoveTopicSubscriptionHandler handler = new RemoveTopicSubscriptionHandler(
                subscriptionManager,
                responseWriter,
                errorWriter);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.reqChannelId(14);

        handler.handle(encode(new CloseSubscriptionRequest().subscriptionId(5)), metadata);

        // when
        futurePool.at(0).complete(null);

        // then
        verify(responseWriter).brokerEventMetadata(metadata);
        verify(responseWriter).dataWriter(requestCaptor.capture());
        verify(responseWriter).tryWriteResponse();
        verify(responseWriter).tryWriteResponse();
        verify(errorWriter, never()).tryWriteResponse();
        verify(errorWriter, never()).tryWriteResponseOrLogFailure();

        final CloseSubscriptionRequest request = requestCaptor.getValue();
        assertThat(request.getSubscriptionId()).isEqualTo(5);
    }

    @Test
    public void shouldWriteErrorOnFailure()
    {
        // given
        final RemoveTopicSubscriptionHandler handler = new RemoveTopicSubscriptionHandler(
                subscriptionManager,
                responseWriter,
                errorWriter);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.reqChannelId(14);

        final DirectBuffer request = encode(new CloseSubscriptionRequest().subscriptionId(5L));
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
