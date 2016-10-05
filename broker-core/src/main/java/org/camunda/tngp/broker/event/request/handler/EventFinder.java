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

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogReaderImpl;

public class EventFinder implements LogEntryHandler<EventReader>
{
    protected final LogReader logReader;
    protected final LogEntryProcessor<EventReader> logEntryProcessor;

    protected EventReader event;
    protected long eventPosition;

    public EventFinder()
    {
        this(new LogReaderImpl(1024 * 1024));
    }

    public EventFinder(LogReader logReader)
    {
        this.logReader = logReader;
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new EventReader(), this);
    }

    void init(
            Log log,
            long position)
    {
        this.logReader.setLogAndPosition(log, position);

        this.event = null;
        this.eventPosition = -1;
    }

    @Override
    public int handle(long position, EventReader reader)
    {
        // no filtering of events

        event = reader;
        eventPosition = position;

        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }

    public boolean findEvents()
    {
        int entriesProcessed = 0;
        do
        {
            entriesProcessed = logEntryProcessor.doWorkSingle();
        } while (entriesProcessed > 0 && event == null);

        return event != null;
    }

    public EventReader getEvent()
    {
        return event;
    }

    public long getEventPosition()
    {
        return eventPosition;
    }

}
