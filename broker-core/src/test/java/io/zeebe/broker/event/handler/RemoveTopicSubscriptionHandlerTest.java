package io.zeebe.broker.event.handler;

import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME_BUFFER;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.broker.event.processor.CloseSubscriptionRequest;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.test.util.FluentMock;
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
        when(subscriptionService.closeSubscriptionAsync(any(DirectBuffer.class), anyInt(), anyLong())).thenAnswer((invocation) -> futurePool.next());
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
                .setTopicName(DEFAULT_TOPIC_NAME_BUFFER)
                .setPartitionId(0));
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
