package org.camunda.tngp.broker.util.mocks;

import java.util.LinkedList;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.ReflectUtil;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class BufferWriterResultCollector
{

    protected List<byte[]> entries = new LinkedList<>();

    public void add(BufferWriter writer)
    {
        final byte[] bytes = new byte[writer.getLength()];
        writer.write(new UnsafeBuffer(bytes), 0);
        entries.add(bytes);
    }

    public int size()
    {
        return entries.size();
    }

    public <T extends BufferReader> T getEntryAs(int index, Class<T> bufferReaderClass)
    {
        final byte[] bytes = entries.get(index);
        final T reader = ReflectUtil.newInstance(bufferReaderClass);
        reader.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return reader;
    }

}
