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

public class PollEventsRequestReaderTest
{
    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

    protected static final int OFFSET = 21;

    protected static final byte[] TOPIC_NAME = "topic".getBytes();

    protected int messageLength;

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final PollEventsEncoder requestEncoder = new PollEventsEncoder();

        headerEncoder.wrap(buffer, OFFSET)
            .blockLength(requestEncoder.sbeBlockLength())
            .resourceId(123)
            .schemaId(requestEncoder.sbeSchemaId())
            .shardId(987)
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaId());

        requestEncoder.wrap(buffer, OFFSET + headerEncoder.encodedLength())
            .startPosition(100)
            .maxEvents(10)
            .putTopicName(TOPIC_NAME, 0, TOPIC_NAME.length);

        messageLength = requestEncoder.limit();
    }

    @Test
    public void shouldReadFromBuffer()
    {
        // given
        final PollEventsRequestReader reader = new PollEventsRequestReader();

        // when
        reader.wrap(buffer, OFFSET, messageLength);

        // then
        assertThat(reader.startPosition()).isEqualTo(100);
        assertThat(reader.maxEvents()).isEqualTo(10);

        assertThatBuffer(reader.topicName())
            .hasCapacity(TOPIC_NAME.length)
            .hasBytes(TOPIC_NAME);
    }


}
