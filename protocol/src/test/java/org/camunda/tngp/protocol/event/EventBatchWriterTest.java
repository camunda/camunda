/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.event.EventBatchDecoder.EventsDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class EventBatchWriterTest
{
    protected static final int OFFSET = 21;

    protected static final byte[] EVENT1 = "event1".getBytes();
    protected static final byte[] EVENT2 = "event2".getBytes();

    protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldWriteRequest()
    {
        // given
        final EventBatchWriter batchWriter = new EventBatchWriter();

        batchWriter
            .appendEvent(0,
                    asBuffer(EVENT1),
                    0,
                    EVENT1.length)
            .appendEvent(100,
                    asBuffer(EVENT2),
                    0,
                    EVENT2.length);

        // when
        batchWriter.write(buffer, OFFSET);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final EventBatchDecoder responseDecoder = new EventBatchDecoder();

        headerDecoder.wrap(buffer, OFFSET);

        assertThat(headerDecoder.blockLength()).isEqualTo(EventBatchDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.schemaId()).isEqualTo(EventBatchDecoder.SCHEMA_ID);
        assertThat(headerDecoder.templateId()).isEqualTo(EventBatchDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.version()).isEqualTo(EventBatchDecoder.SCHEMA_VERSION);

        responseDecoder.wrap(buffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final EventsDecoder eventsDecoder = responseDecoder.events();

        // first event
        eventsDecoder.next();

        assertThat(eventsDecoder.position()).isEqualTo(0);

        final UnsafeBuffer eventBuffer1 = new UnsafeBuffer(new byte[EVENT1.length]);
        eventsDecoder.getEvent(eventBuffer1, 0, eventsDecoder.eventLength());

        assertThatBuffer(eventBuffer1)
            .hasCapacity(EVENT1.length)
            .hasBytes(EVENT1);

        // second event
        eventsDecoder.next();

        assertThat(eventsDecoder.position()).isEqualTo(100);

        final UnsafeBuffer eventBuffer2 = new UnsafeBuffer(new byte[EVENT2.length]);
        eventsDecoder.getEvent(eventBuffer2, 0, eventsDecoder.eventLength());

        assertThatBuffer(eventBuffer2)
            .hasCapacity(EVENT2.length)
            .hasBytes(EVENT2);
    }

    @Test
    public void shouldReturnLength()
    {
        // given
        final EventBatchWriter batchWriter = new EventBatchWriter();

        batchWriter
            .appendEvent(0,
                    asBuffer(EVENT1),
                    0,
                    EVENT1.length)
            .appendEvent(100,
                    asBuffer(EVENT2),
                    0,
                    EVENT2.length);

        // when
        final int length = batchWriter.getLength();

        // then
        int expectedLength =
                MessageHeaderEncoder.ENCODED_LENGTH +
                EventBatchDecoder.BLOCK_LENGTH +
                EventsDecoder.sbeHeaderSize();

        expectedLength += (EventsDecoder.sbeBlockLength() + EventsDecoder.eventHeaderLength()) * 2; // static length of events
        expectedLength += EVENT1.length + EVENT2.length; // event lengths

        assertThat(length).isEqualTo(expectedLength);
    }

    @Test
    public void shouldResetAfterWrite()
    {
        // given
        final EventBatchWriter batchWriter = new EventBatchWriter();

        batchWriter
            .appendEvent(0,
                asBuffer(EVENT1),
                0,
                EVENT1.length);

        // when
        batchWriter.write(buffer, OFFSET);
        batchWriter.write(buffer, OFFSET);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final EventBatchDecoder responseDecoder = new EventBatchDecoder();

        headerDecoder.wrap(buffer, OFFSET);

        responseDecoder.wrap(buffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(responseDecoder.events().count()).isEqualTo(0);
    }

    @Test
    public void shouldValidateEventCount()
    {
        // given
        final EventBatchWriter batchWriter = new EventBatchWriter();
        // no event is set

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("No events set");

        // when
        batchWriter.validate();
    }

    protected UnsafeBuffer asBuffer(byte[] event)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
        buffer.wrap(event);
        return buffer;
    }

}
