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
    protected Long2ObjectHashMap<byte[]> logEntries = new Long2ObjectHashMap<>();
    protected List<Long> entryPositions = new ArrayList<>();

    protected UnsafeBuffer tempWriteBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    protected Log targetLog;

    public StubLogReader(Log targetLog)
    {
        this(0L, targetLog);
    }

    public StubLogReader(long initialPosition, Log targetLog)
    {
        this.tailPosition = initialPosition;
        this.position = initialPosition;
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
            return false;
        }


        final byte[] entryAtPosition = logEntries.get(position);
        final UnsafeBuffer tempReadBuffer = new UnsafeBuffer(entryAtPosition);

        reader.wrap(tempReadBuffer, 0, entryAtPosition.length);

        position += entryAtPosition.length;

        return true;
    }

    public StubLogReader addEntry(BufferWriter writer)
    {
        final int writeLength = writer.getLength();

        writer.write(tempWriteBuffer, 0);

        final byte[] entry = new byte[writeLength];
        tempWriteBuffer.getBytes(0, entry);

        logEntries.put(tailPosition, entry);
        entryPositions.add(tailPosition);
        tailPosition += writeLength;
        return this;
    }

}
