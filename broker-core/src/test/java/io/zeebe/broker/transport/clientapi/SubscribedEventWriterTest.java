/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.transport.clientapi;

import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.SubscribedEventDecoder;
import io.zeebe.protocol.clientapi.SubscriptionType;

public class SubscribedEventWriterTest
{

    protected static final DirectBuffer BUFFER = wrapString("foo");

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SubscribedEventDecoder bodyDecoder = new SubscribedEventDecoder();

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldWriteEventToBuffer()
    {
        // given
        final SubscribedEventWriter eventWriter = new SubscribedEventWriter(null);
        eventWriter
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
}
