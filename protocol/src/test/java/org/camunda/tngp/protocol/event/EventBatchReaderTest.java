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
import org.junit.Before;
import org.junit.Test;

public class EventBatchReaderTest
{
    protected static final int OFFSET = 21;

    protected static final byte[] EVENT1 = "event1".getBytes();
    protected static final byte[] EVENT2 = "event2".getBytes();

    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

    protected int messageLength;

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final EventBatchEncoder batchEncoder = new EventBatchEncoder();

        headerEncoder.wrap(buffer, OFFSET)
            .blockLength(batchEncoder.sbeBlockLength())
            .schemaId(batchEncoder.sbeSchemaId())
            .templateId(batchEncoder.sbeTemplateId())
            .version(batchEncoder.sbeSchemaVersion());

        batchEncoder
            .wrap(buffer, OFFSET + headerEncoder.encodedLength())
            .eventsCount(2)
            .next()
                .position(0)
                .putEvent(EVENT1, 0, EVENT1.length)
            .next()
                .position(100)
                .putEvent(EVENT2, 0, EVENT2.length);

        messageLength = batchEncoder.limit();
    }

    @Test
    public void shouldReadResponseFromBuffer()
    {
        // given
        final EventBatchReader reader = new EventBatchReader();

        // when
        reader.wrap(buffer, OFFSET, messageLength);

        // then
        assertThat(reader.eventCount()).isEqualTo(2);

        reader.nextEvent();

        assertThat(reader.currentPosition()).isEqualTo(0);
        assertThatBuffer(reader.currentEvent())
            .hasCapacity(EVENT1.length)
            .hasBytes(EVENT1);

        reader.nextEvent();

        assertThat(reader.currentPosition()).isEqualTo(100);
        assertThatBuffer(reader.currentEvent())
            .hasCapacity(EVENT2.length)
            .hasBytes(EVENT2);
    }

}
