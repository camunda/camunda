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
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.event.EventBatchDecoder.EventsDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class EventBatchReader implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    protected final EventBatchDecoder batchDecoder = new EventBatchDecoder();

    protected UnsafeBuffer inputBuffer = new UnsafeBuffer(0, 0);

    protected EventsDecoder eventsDecoder;
    protected final UnsafeBuffer eventBuffer = new UnsafeBuffer(0, 0);


    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        inputBuffer.wrap(buffer, offset, length);

        headerDecoder.wrap(inputBuffer, 0);

        batchDecoder.wrap(inputBuffer, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        eventsDecoder = batchDecoder.events();
    }

    public EventBatchReader nextEvent()
    {
        eventsDecoder.next();

        final int offset = batchDecoder.limit() + EventsDecoder.eventHeaderLength();

        eventBuffer.wrap(
                inputBuffer,
                offset,
                eventsDecoder.eventLength());

        batchDecoder.limit(offset + eventsDecoder.eventLength());

        return this;
    }

    public int eventCount()
    {
        return eventsDecoder.count();
    }

    public long currentPosition()
    {
        return eventsDecoder.position();
    }

    public DirectBuffer currentEvent()
    {
        return eventBuffer;
    }

}
