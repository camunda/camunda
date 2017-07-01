package io.zeebe.broker.transport.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.Mockito.verify;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.SubscribedEventDecoder;
import io.zeebe.protocol.clientapi.SubscriptionType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SubscribedEventWriterTest
{

    protected static final DirectBuffer BUFFER = wrapString("foo");

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SubscribedEventDecoder bodyDecoder = new SubscribedEventDecoder();

    @Mock
    protected SingleMessageWriter singleMessageWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldWriteEventToBuffer()
    {
        // given
        final SubscribedEventWriter eventWriter = new SubscribedEventWriter(singleMessageWriter);
        eventWriter.channelId(123)
            .event(BUFFER, 1, BUFFER.capacity() - 1)
            .eventType(EventType.RAFT_EVENT)
            .key(123L)
            .position(546L)
            .topicName(wrapString("test-topic"))
            .partitionId(876)
            .subscriberKey(4L)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[eventWriter.getLength() + 2]);

        // when
        eventWriter.write(buffer, 2);

        // then
        headerDecoder.wrap(buffer, 2);
        bodyDecoder.wrap(buffer, 2 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.eventType()).isEqualTo(EventType.RAFT_EVENT);
        assertThat(bodyDecoder.key()).isEqualTo(123L);
        assertThat(bodyDecoder.position()).isEqualTo(546L);
        assertThat(bodyDecoder.topicName()).isEqualTo("test-topic");
        assertThat(bodyDecoder.partitionId()).isEqualTo(876);
        assertThat(bodyDecoder.subscriberKey()).isEqualTo(4L);

        final UnsafeBuffer eventBuffer = new UnsafeBuffer(new byte[bodyDecoder.eventLength()]);
        bodyDecoder.getEvent(eventBuffer, 0, eventBuffer.capacity());

        assertThatBuffer(eventBuffer).hasBytes(BUFFER, 1, BUFFER.capacity() - 1);
    }

    @Test
    public void shouldWriteAsSingleMessage()
    {
        // given
        final SubscribedEventWriter eventWriter = new SubscribedEventWriter(singleMessageWriter);
        eventWriter.channelId(123)
            .event(BUFFER, 1, BUFFER.capacity() - 1);

        // when
        eventWriter.tryWriteMessage();

        // then
        verify(singleMessageWriter).tryWrite(123, eventWriter);
    }
}
