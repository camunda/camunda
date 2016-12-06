package org.camunda.tngp.broker.util.mocks;


import org.camunda.tngp.broker.logstreams.LogEntryWriter;
import org.camunda.tngp.broker.logstreams.LogWriter;
import org.camunda.tngp.util.buffer.BufferReader;

// TODO: make LogWriter an interface
public class StubLogWriter extends LogWriter
{

    protected BufferWriterResultCollector collector = new BufferWriterResultCollector();
    protected long tailPosition = 0L;

    public StubLogWriter()
    {
        super(null);
    }

    @Override
    public long write(LogEntryWriter<?, ?> writer)
    {
        final int length = writer.getEncodedLength();
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
