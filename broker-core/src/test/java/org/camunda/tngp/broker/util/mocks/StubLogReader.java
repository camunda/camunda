package org.camunda.tngp.broker.util.mocks;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.collections.Long2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class StubLogReader implements LogReader
{
    protected long position;
    protected long tailPosition;
    protected Long2ObjectHashMap<BufferWriter> logEntries = new Long2ObjectHashMap<>();
    protected List<Long> entryPositions = new ArrayList<>();

    protected UnsafeBuffer tempBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    protected Log targetLog;

    public StubLogReader(Log targetLog)
    {
        this(0L, targetLog);
    }

    public StubLogReader(long initialPosition, Log targetLog)
    {
        this.tailPosition = initialPosition;
        this.targetLog = targetLog;
    }

    public long position()
    {
        return position;
    }

    @Override
    public void setLogAndPosition(Log log, long position)
    {
        if (targetLog != log)
        {
            throw new RuntimeException("StubLogReader only works for a single log");
        }

        this.position = position;
    }

    @Override
    public void setPosition(long position)
    {
        this.position = position;
    }

    public long getEntryPosition(int entryIndex)
    {
        return entryPositions.get(entryIndex);
    }

    @Override
    public boolean read(BufferReader reader)
    {
        if (!logEntries.containsKey(position))
        {
            throw new RuntimeException("There is no event at position " + position);
        }

        final BufferWriter entryAtPosition = logEntries.get(position);
        entryAtPosition.write(tempBuffer, 0);
        final int writtenLength = entryAtPosition.getLength();

        reader.wrap(tempBuffer, 0, writtenLength);

        position += writtenLength;

        return logEntries.containsKey(position);
    }

    public StubLogReader addEntry(BufferWriter writer)
    {
        logEntries.put(tailPosition, writer);
        entryPositions.add(tailPosition);
        tailPosition += writer.getLength();
        return this;
    }

}
