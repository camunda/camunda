package io.zeebe.broker.event.handler;

import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME_BUFFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.zeebe.broker.event.processor.CloseSubscriptionRequest;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.transport.clientapi.BufferingServerOutput;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.test.util.BufferAssert;
import io.zeebe.test.util.FluentMock;

public class RemoveTopicSubscriptionHandlerTest
{

    protected FuturePool futurePool;

    @Mock
    protected TopicSubscriptionService subscriptionService;

    @FluentMock
    protected ControlMessageResponseWriter responseWriter;

    @FluentMock
    protected ErrorResponseWriter errorWriter;

    protected BufferingServerOutput output;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        output = new BufferingServerOutput();

        futurePool = new FuturePool();
        when(subscriptionService.closeSubscriptionAsync(any(DirectBuffer.class), anyInt(), anyLong())).thenAnswer((invocation) -> futurePool.next());
    }

    @Test
    public void shouldWriteErrorOnFailure()
    {
        // given
        final RemoveTopicSubscriptionHandler handler = new RemoveTopicSubscriptionHandler(output, subscriptionService);

        final BrokerEventMetadata metadata = new BrokerEventMetadata();
        metadata.requestStreamId(14);

        final DirectBuffer request = encode(new CloseSubscriptionRequest()
                .setSubscriberKey(5L)
                .setTopicName(DEFAULT_TOPIC_NAME_BUFFER)
                .setPartitionId(0));
        handler.handle(request, metadata);

        // when
        futurePool.at(0).completeExceptionally(new RuntimeException("foo"));

        // then
        assertThat(output.getSentResponses()).hasSize(1);

        final ErrorResponseDecoder errorDecoder = output.getAsErrorResponse(0);

        assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorDecoder.errorData()).isEqualTo("Cannot close topic subscription. foo");

        final UnsafeBuffer failedRequestBuf = new UnsafeBuffer(new byte[errorDecoder.failedRequestLength()]);
        errorDecoder.getFailedRequest(failedRequestBuf, 0, failedRequestBuf.capacity());
        BufferAssert.assertThatBuffer(failedRequestBuf).hasBytes(request);
    }

    protected static final DirectBuffer encode(UnpackedObject obj)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[obj.getLength()]);
        obj.write(buffer, 0);
        return buffer;
    }
}
