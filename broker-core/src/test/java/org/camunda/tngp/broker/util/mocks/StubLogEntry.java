package org.camunda.tngp.broker.util.mocks;

import org.agrona.DirectBuffer;
import org.camunda.tngp.log.ReadableLogEntry;
import org.camunda.tngp.util.buffer.BufferReader;

public class StubLogEntry implements ReadableLogEntry
{
    protected final long longKey;
    protected final long position;
    protected final DirectBuffer valueBuffer;

    public StubLogEntry(long position, long longKey, DirectBuffer valueBuffer)
    {
        this.position = position;
        this.longKey = longKey;
        this.valueBuffer = valueBuffer;
    }

    @Override
    public long getPosition()
    {
        return position;
    }

    @Override
    public long getLongKey()
    {
        return longKey;
    }

    @Override
    public DirectBuffer getValueBuffer()
    {
        return valueBuffer;
    }

    @Override
    public int getValueOffset()
    {
        return 0;
    }

    @Override
    public int getValueLength()
    {
        return valueBuffer.capacity();
    }

    @Override
    public void readValue(BufferReader reader)
    {
        reader.wrap(valueBuffer, getValueOffset(), getValueLength());
    }


}
