package org.camunda.tngp.broker.util.mocks;


import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.collections.Long2LongHashMap;

// TODO: make LogWriter an interface
public class StubLogWriter extends LogWriter
{

    public StubLogWriter()
    {
        super(null);
    }

    protected BufferWriterResultCollector collector = new BufferWriterResultCollector();
    protected Long2LongHashMap positionMap = new Long2LongHashMap(-1L);
    protected long tailPosition = 0L;

    @Override
    public long write(BufferWriter writer)
    {
        final int length = writer.getLength();
        collector.add(writer);

        final long result = tailPosition;
        tailPosition += length;
        return result;
    }

    public int size()
    {
        return collector.size();
    }

    public <T extends BufferReader> T getEntryAs(int index, Class<T> bufferReaderClass)
    {
        return collector.getEntryAs(index, bufferReaderClass);
    }
}
