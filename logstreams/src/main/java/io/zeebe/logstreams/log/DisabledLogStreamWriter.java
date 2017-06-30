package io.zeebe.logstreams.log;

import org.agrona.DirectBuffer;
import io.zeebe.util.buffer.BufferWriter;

public class DisabledLogStreamWriter implements LogStreamWriter
{

    @Override
    public void wrap(LogStream log)
    {
    }

    @Override
    public LogStreamWriter positionAsKey()
    {
        return this;
    }

    @Override
    public LogStreamWriter key(long key)
    {
        return this;
    }

    @Override
    public LogStreamWriter sourceEvent(final DirectBuffer logStreamTopicName, int logStreamPartitionId, long position)
    {
        return this;
    }

    @Override
    public LogStreamWriter producerId(int producerId)
    {
        return this;
    }

    @Override
    public LogStreamWriter metadata(DirectBuffer buffer, int offset, int length)
    {
        return this;
    }

    @Override
    public LogStreamWriter metadata(DirectBuffer buffer)
    {
        return this;
    }

    @Override
    public LogStreamWriter metadataWriter(BufferWriter writer)
    {
        return this;
    }

    @Override
    public LogStreamWriter value(DirectBuffer value, int valueOffset, int valueLength)
    {
        return this;
    }

    @Override
    public LogStreamWriter value(DirectBuffer value)
    {
        return this;
    }

    @Override
    public LogStreamWriter valueWriter(BufferWriter writer)
    {
        return this;
    }

    @Override
    public void reset()
    {
    }

    @Override
    public long tryWrite()
    {
        throw new RuntimeException("Cannot write event; Writing is disabled");
    }

}
