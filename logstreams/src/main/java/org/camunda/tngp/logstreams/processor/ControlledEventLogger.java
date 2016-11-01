package org.camunda.tngp.logstreams.processor;

import org.agrona.DirectBuffer;
import org.camunda.tngp.logstreams.EventLogger;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ControlledEventLogger
{
    protected final EventLogger delegate;

    protected boolean writeRequested;

    public ControlledEventLogger(EventLogger delegate)
    {
        this.delegate = delegate;
    }

    public ControlledEventLogger positionAsKey()
    {
        delegate.positionAsKey();
        return this;
    }

    public ControlledEventLogger key(long key)
    {
        delegate.key(key);
        return this;
    }

    public ControlledEventLogger value(DirectBuffer value, int valueOffset, int valueLength)
    {
        delegate.value(value, valueOffset, valueLength);
        return this;
    }

    public ControlledEventLogger value(DirectBuffer value)
    {
        delegate.value(value);
        return this;
    }

    public ControlledEventLogger valueWriter(BufferWriter writer)
    {
        delegate.valueWriter(writer);
        return this;
    }

    public EventLogger getDelegate()
    {
        return delegate;
    }

    public void requestWrite()
    {
        this.writeRequested = true;
    }

    public void reset()
    {
        this.writeRequested = false;
    }

    public boolean isWriteRequested()
    {
        return writeRequested;
    }

}
