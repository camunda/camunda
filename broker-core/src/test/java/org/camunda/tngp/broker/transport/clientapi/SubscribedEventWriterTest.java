package org.camunda.tngp.broker.transport.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventDecoder;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SubscribedEventWriterTest
{

    protected static final DirectBuffer BUFFER = new UnsafeBuffer("foo".getBytes(StandardCharsets.UTF_8));

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
            .longKey(123L)
            .position(546L)
            .topicId(876)
            .subscriptionId(4L)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[eventWriter.getLength() + 2]);

        // when
        eventWriter.write(buffer, 2);

        // then
        headerDecoder.wrap(buffer, 2);
        bodyDecoder.wrap(buffer, 2 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.eventType()).isEqualTo(EventType.RAFT_EVENT);
        assertThat(bodyDecoder.longKey()).isEqualTo(123L);
        assertThat(bodyDecoder.position()).isEqualTo(546L);
        assertThat(bodyDecoder.topicId()).isEqualTo(876);
        assertThat(bodyDecoder.subscriptionId()).isEqualTo(4L);

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
