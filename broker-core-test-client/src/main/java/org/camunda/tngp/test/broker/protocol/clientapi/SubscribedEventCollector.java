package org.camunda.tngp.test.broker.protocol.clientapi;

import java.util.function.Supplier;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class SubscribedEventCollector implements TransportChannelHandler, Supplier<SubscribedEvent>
{

    protected final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    protected int readOffset = 0;
    protected int writeOffset = 0;

    protected Object monitor = new Object();
    protected static final long MAX_WAIT = 10 * 1000L;

    @Override
    public SubscribedEvent get()
    {
        if (readOffset >= writeOffset)
        {
            // block haven't got enough events yet
            try
            {
                synchronized (monitor)
                {
                    monitor.wait(MAX_WAIT);
                }
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

        if (readOffset >= writeOffset)
        {
            // if still not available
            throw new RuntimeException("no more events available");
        }

        final SubscribedEvent event = new SubscribedEvent();

        final int eventLength = buffer.getInt(readOffset);
        readOffset += BitUtil.SIZE_OF_INT;

        event.wrap(buffer, readOffset, eventLength);
        readOffset += eventLength;

        return event;
    }

    @Override
    public void onChannelOpened(TransportChannel transportChannel)
    {
    }

    @Override
    public void onChannelClosed(TransportChannel transportChannel)
    {
    }

    @Override
    public void onChannelSendError(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        throw new RuntimeException("SubscribedEventCollector received error message");
    }

    @Override
    public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        final int eventLength = length - TransportHeaderDescriptor.HEADER_LENGTH;
        this.buffer.putInt(writeOffset, eventLength);
        writeOffset += BitUtil.SIZE_OF_INT;
        this.buffer.putBytes(writeOffset, buffer, offset + TransportHeaderDescriptor.HEADER_LENGTH, eventLength);
        writeOffset += eventLength;

        synchronized (monitor)
        {
            monitor.notifyAll();
        }

        return true;
    }

    @Override
    public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        throw new RuntimeException("SubscribedEventCollector received control frame");
    }

    public void moveToTail()
    {
        this.readOffset = writeOffset;
    }

}
