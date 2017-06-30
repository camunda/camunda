package io.zeebe.logstreams.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;

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
            throw new NoSuchElementException();
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

    protected static class Entry
    {
        long position;
        LoggedEvent event;
    }
}
