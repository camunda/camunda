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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferWriter;

public class PollEventsRequestWriter implements BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final PollEventsEncoder requestEncoder = new PollEventsEncoder();

    protected final UnsafeBuffer topicNameBuffer = new UnsafeBuffer(0, 0);

    protected long startPosition;
    protected int maxEvents;

    public PollEventsRequestWriter()
    {
        reset();
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
               requestEncoder.sbeBlockLength() +
               PollEventsEncoder.topicNameHeaderLength() +
               topicNameBuffer.capacity();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(requestEncoder.sbeBlockLength())
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        requestEncoder.wrap(buffer, offset)
            .startPosition(startPosition)
            .maxEvents(maxEvents)
            .putTopicName(topicNameBuffer, 0, topicNameBuffer.capacity());

        reset();
    }

    public void validate()
    {
        if (topicNameBuffer.capacity() == 0)
        {
            throw new RuntimeException("No topic name set");
        }
        if (startPosition < 0)
        {
            throw new RuntimeException("start position must be greater or equal to 0");
        }
        if (maxEvents <= 0)
        {
            throw new RuntimeException("max events must be greater than 0");
        }
    }

    protected void reset()
    {
        startPosition = PollEventsEncoder.startPositionNullValue();
        maxEvents = PollEventsEncoder.maxEventsNullValue();

        topicNameBuffer.wrap(0, 0);
    }

    public PollEventsRequestWriter startPosition(long startPosition)
    {
        this.startPosition = startPosition;
        return this;
    }

    public PollEventsRequestWriter maxEvents(int maxEvents)
    {
        this.maxEvents = maxEvents;
        return this;
    }

    public PollEventsRequestWriter topicName(byte[] bytes, int offset, int length)
    {
        topicNameBuffer.wrap(bytes, offset, length);
        return this;
    }

}
