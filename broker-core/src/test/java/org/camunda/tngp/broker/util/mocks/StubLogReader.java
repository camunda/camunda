package org.camunda.tngp.broker.util.mocks;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.ReadableLogEntry;
import org.camunda.tngp.util.buffer.BufferWriter;

public class StubLogReader implements LogReader
{
    private enum IteratorState
    {
        UNINITIALIZED,
        INITIALIZED_EMPTY_LOG,
        INITIALIZED,
        ACTIVE;
    }

    protected IteratorState iteratorState = IteratorState.UNINITIALIZED;

    protected List<StubLogEntry> logEntries = new ArrayList<>();
    protected StubLogEntry currentEntry = null;

    protected Log targetLog;

    protected long firstEventPosition = 0;

    public StubLogReader(Log targetLog)
    {
        wrap(targetLog);
    }

    public StubLogReader(Log targetLog, long position)
    {
        wrap(targetLog, position);
    }

    public long getPosition()
    {
        if (currentEntry != null)
        {
            return currentEntry.getPosition();
        }
        else
        {
            return -1;
        }
    }

    @Override
    public void wrap(Log log)
    {
        if (targetLog != null && targetLog != log)
        {
            throw new RuntimeException("StubLogReader only works for a single log");
        }

        seekToLastEntry();
    }

    @Override
    public void wrap(Log log, long position)
    {
        if (targetLog != null && targetLog != log)
        {
            throw new RuntimeException("StubLogReader only works for a single log");
        }

        seek(position);
    }

    private void clear()
    {
        this.iteratorState = IteratorState.UNINITIALIZED;
        this.currentEntry = null;
    }

    @Override
    public void seek(long position)
    {
        clear();

        for (int i = 0; i < logEntries.size(); i++)
        {
            final StubLogEntry entry = logEntries.get(i);
            if (entry.getPosition() >= position)
            {
                this.iteratorState = IteratorState.INITIALIZED;
                this.currentEntry = entry;
                break;
            }
        }

    }

    @Override
    public boolean hasNext()
    {
        switch (iteratorState)
        {
            case UNINITIALIZED:
                return false;

            case INITIALIZED:
                return true;

            case INITIALIZED_EMPTY_LOG:
                return logEntries.size() > 0;

            default: // ACTIVE
                return logEntries.indexOf(currentEntry) < logEntries.size() - 1;

        }
    }

    @Override
    public ReadableLogEntry next()
    {
        if (!hasNext())
        {
            throw new NoSuchElementException("No next entry available. Check with hasNext() first.");
        }

        StubLogEntry nextEntry = currentEntry;

        if (iteratorState == IteratorState.ACTIVE)
        {
            nextEntry = logEntries.get(logEntries.indexOf(currentEntry) + 1);
        }
        else if (iteratorState == IteratorState.INITIALIZED_EMPTY_LOG)
        {
            nextEntry = logEntries.get(0);
        }

        iteratorState = IteratorState.ACTIVE;
        currentEntry = nextEntry;

        return nextEntry;
    }

    public StubLogReader addEntry(long key, BufferWriter writer)
    {
        final int writeLength = writer.getLength();

        final UnsafeBuffer entryBuffer = new UnsafeBuffer(new byte[writeLength]);

        writer.write(entryBuffer, 0);

        long position = firstEventPosition;

        if (logEntries.size() >= 1)
        {
            position = logEntries.get(logEntries.size() - 1).getPosition() + 1;
        }

        logEntries.add(new StubLogEntry(position, 0, entryBuffer));

        return this;
    }

    public StubLogReader addEntry(BufferWriter writer)
    {
        return addEntry(-1, writer);
    }

    @Override
    public void seekToLastEntry()
    {
        clear();

        if (logEntries.size() > 0)
        {
            currentEntry = logEntries.get(logEntries.size() - 1);
            this.iteratorState = IteratorState.INITIALIZED;
        }
        else
        {
            iteratorState = IteratorState.INITIALIZED_EMPTY_LOG;
        }
    }

    @Override
    public void seekToFirstEntry()
    {
        clear();

        if (logEntries.size() > 0)
        {
            currentEntry = logEntries.get(0);
            this.iteratorState = IteratorState.INITIALIZED;
        }
        else
        {
            iteratorState = IteratorState.INITIALIZED_EMPTY_LOG;
        }

    }

    public long getEntryPosition(int i)
    {
        return logEntries.get(i).getPosition();
    }

    public void setFirstEventPosition(long intitalPosition)
    {
        this.firstEventPosition = intitalPosition;
    }
}