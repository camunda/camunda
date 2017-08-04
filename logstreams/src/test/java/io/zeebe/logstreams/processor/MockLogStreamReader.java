/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockLogStreamReader implements LogStreamReader
{

    protected LogStream mockingLog;
    protected int iteratorPosition = -1;
    protected long position;

    protected List<Entry> events = new ArrayList<>();

    /**
     * Counting these as they are the expensive ones with {@link BufferedLogStreamReader}
     */
    protected int hasNextInvocations = 0;


    public void addEvent(LoggedEvent event)
    {
        final Entry entry = new Entry();
        entry.position = event.getPosition();
        entry.event = event;
        this.events.add(entry);
    }

    @Override
    public boolean hasNext()
    {
        hasNextInvocations++;
        return iteratorPosition < events.size() - 1;
    }

    @Override
    public LoggedEvent next()
    {
        iteratorPosition++;
        position = events.get(iteratorPosition).position;
        return events.get(iteratorPosition).event;
    }

    @Override
    public void wrap(LogStream log)
    {
        wrap(log, 0);
    }

    @Override
    public void wrap(LogStream log, long position)
    {
        if (mockingLog != null && log != mockingLog)
        {
            throw new RuntimeException("not implemented");
        }
        mockingLog = log;
        seek(position);
    }

    @Override
    public boolean seek(long position)
    {
        this.iteratorPosition = -1;

        while (iteratorPosition < events.size() - 1)
        {
            final Entry nextEntry = events.get(iteratorPosition + 1);
            if (nextEntry.position == position)
            {
                return true;
            }
            else if (nextEntry.position > position)
            {
                return false;
            }

            this.iteratorPosition++;
        }

        this.position = position;
        return false;
    }

    @Override
    public void seekToFirstEvent()
    {
        seek(0);
    }

    @Override
    public void seekToLastEvent()
    {
        seek(events.isEmpty() ? 0 : events.get(events.size() - 1).position);
    }

    @Override
    public long getPosition()
    {
        if (events.isEmpty())
        {
            return -1L;
        }

        return position;
    }

    public int getHasNextInvocations()
    {
        return hasNextInvocations;
    }

    public LogStream getMockingLog()
    {
        return mockingLog;
    }

    private AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public boolean isClosed()
    {
        return closed.get();
    }

    @Override
    public void reOpen(LogStream logStream)
    {
        closed.compareAndSet(true, false);
    }

    @Override
    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            events.clear();
        }
    }

    protected static class Entry
    {
        long position;
        LoggedEvent event;
    }
}
