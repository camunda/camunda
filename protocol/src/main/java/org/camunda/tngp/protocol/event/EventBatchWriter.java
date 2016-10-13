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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.event.EventBatchEncoder.EventsEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import static org.agrona.BitUtil.*;

public class EventBatchWriter implements BufferWriter
{
    public static final int DEFAULT_EVENT_BUFFER_SIZE = 1024 * 1024;

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final EventBatchEncoder batchEncoder = new EventBatchEncoder();

    protected final UnsafeBuffer eventBuffer;
    protected final int bufferCapacity;

    protected int eventBufferLimit = 0;
    protected int eventCount = 0;

    public EventBatchWriter()
    {
        this(DEFAULT_EVENT_BUFFER_SIZE);
    }

    public EventBatchWriter(int eventBufferSize)
    {
        bufferCapacity = eventBufferSize + Long.BYTES + Integer.BYTES;
        eventBuffer = new UnsafeBuffer(new byte[bufferCapacity]);

        reset();
    }

    @Override
    public int getLength()
    {
        int size = MessageHeaderEncoder.ENCODED_LENGTH +
                EventBatchEncoder.BLOCK_LENGTH +
                EventsEncoder.sbeHeaderSize();

        size += (EventsEncoder.sbeBlockLength() + EventsEncoder.eventHeaderLength()) * eventCount;

        size += eventBufferLimit - (Long.BYTES + Integer.BYTES) * eventCount;

        return size;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(batchEncoder.sbeBlockLength())
            .schemaId(batchEncoder.sbeSchemaId())
            .templateId(batchEncoder.sbeTemplateId())
            .version(batchEncoder.sbeSchemaVersion())
            .resourceId(0)
            .shardId(0);

        batchEncoder.wrap(buffer, offset + headerEncoder.encodedLength());

        final EventsEncoder eventsEncoder = batchEncoder.eventsCount(eventCount);

        int eventOffset = 0;
        for (int i = 0; i < eventCount; i++)
        {
            final long position = eventBuffer.getLong(eventOffset);
            eventOffset += SIZE_OF_LONG;

            final int eventLength = eventBuffer.getShort(eventOffset);
            eventOffset += SIZE_OF_INT;

            eventsEncoder.next()
                .position(position)
                .putEvent(eventBuffer, eventOffset, eventLength);

            eventOffset += eventLength;
        }

        reset();
    }

    protected void reset()
    {
        eventBuffer.wrap(new byte[bufferCapacity]);
        eventBufferLimit = 0;
        eventCount = 0;
    }

    public void validate()
    {
        if (eventCount < 1)
        {
            throw new RuntimeException("No events set");
        }
    }

    public boolean appendEvent(
            long position,
            DirectBuffer buffer,
            int eventOffset,
            int eventLength)
    {
        boolean eventAppended = false;

        if (hasCapacity(eventLength))
        {
            eventBuffer.putLong(eventBufferLimit, position);
            eventBufferLimit += SIZE_OF_LONG;

            eventBuffer.putShort(eventBufferLimit, (short) eventLength);
            eventBufferLimit += SIZE_OF_INT;

            eventBuffer.putBytes(eventBufferLimit, buffer, eventOffset, eventLength);
            eventBufferLimit += eventLength;

            eventCount++;

            eventAppended = true;
        }

        return eventAppended;
    }

    public boolean hasCapacity(int eventLength)
    {
        return bufferCapacity - eventBufferLimit - SIZE_OF_LONG - SIZE_OF_INT >= 0;
    }

}
