package org.camunda.tngp.broker.event.handler;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.event.processor.CloseSubscriptionRequest;
import org.camunda.tngp.broker.event.processor.TopicSubscriptionService;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageResponseWriter;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.test.util.FluentMock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RemoveTopicSubscriptionHandlerTest
{

    protected FuturePool futurePool;

    @Mock
    protected TopicSubscriptionService subscriptionService;

    @FluentMock
    protected ControlMessageResponseWriter responseWriter;

    @FluentMock
    protected ErrorResponseWriter errorWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        futurePool = new FuturePool();
        when(subscriptionService.closeSubscriptionAsync(anyInt(), anyLong())).thenAnswer((invocation) -> futurePool.next());
    }

    @Test
    public void shouldWriteErrorOnFailure()
    {
        // given
        final RemoveTopicSubscriptionHandler handler = new RemoveTopicSubscriptionHandler(
                subscriptionService,
                responseWriter,
                errorWriter);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.reqChannelId(14);

        final DirectBuffer request = encode(new CloseSubscriptionRequest()
                .setSubscriberKey(5L)
                .setTopicId(0));
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
