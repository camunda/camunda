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
package org.camunda.tngp.broker.event.request.handler;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogReaderImpl;

public class EventFinder implements LogEntryHandler<EventReader>
{
    protected final LogReader logReader;
    protected final LogEntryProcessor<EventReader> logEntryProcessor;

    protected final UnsafeBuffer eventBuffer;
    protected final int eventBufferSize;

    protected int maxEvents;
    protected int eventCount;

    protected long[] eventPositions;
    protected int[] eventBufferOffsets;

    public EventFinder(int eventBufferSize)
    {
        this(new LogReaderImpl(eventBufferSize), eventBufferSize);
    }

    public EventFinder(LogReader logReader, int eventBufferSize)
    {
        this.logReader = logReader;
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new EventReader(), this);

        this.eventBufferSize = eventBufferSize;
        eventBuffer = new UnsafeBuffer(new byte[eventBufferSize]);
    }

    public void init(Log log, long position, int maxEvents)
    {
        this.logReader.setLogAndPosition(log, position);

        this.eventBuffer.wrap(new byte[eventBufferSize]);

        this.maxEvents = maxEvents;
        this.eventCount = 0;

        this.eventPositions = new long[maxEvents];
        this.eventBufferOffsets = new int[maxEvents + 1];
        eventBufferOffsets[0] = 0;
    }

    @Override
    public int handle(long position, EventReader reader)
    {
        int result = LogEntryHandler.CONSUME_ENTRY_RESULT;

        final DirectBuffer buffer = reader.getEventBuffer();
        final int bufferLength = buffer.capacity();
        final int bufferIndex = eventBufferOffsets[eventCount];

        if (bufferIndex + bufferLength < eventBufferSize)
        {
            eventPositions[eventCount] = position;
            // add event to buffer
            eventBuffer.putBytes(bufferIndex, buffer, 0, bufferLength);
            // set offset of next event
            eventBufferOffsets[eventCount + 1] = bufferIndex + bufferLength;

            eventCount++;
        }
        else
        {
            result = LogEntryHandler.FAILED_ENTRY_RESULT;
        }

        return result;
    }

    public int findEvents()
    {
        int entriesProcessed = 0;
        do
        {
            entriesProcessed = logEntryProcessor.doWork(maxEvents);
        } while (entriesProcessed > 0 && eventCount > maxEvents);

        return eventCount;
    }

    public DirectBuffer getEventBuffer()
    {
        return eventBuffer;
    }

    public long getEventPosition(int index)
    {
        return eventPositions[index];
    }

    public int getEventBufferOffset(int index)
    {
        return eventBufferOffsets[index];
    }

    public int getEventBufferLength(int index)
    {
        return eventBufferOffsets[index + 1] - eventBufferOffsets[index];
    }

}
