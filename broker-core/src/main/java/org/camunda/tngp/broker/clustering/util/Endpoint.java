package org.camunda.tngp.broker.clustering.util;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;

import java.io.UnsupportedEncodingException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class Endpoint implements Comparable<Endpoint>, BufferWriter, BufferReader
{
    public static final int MAX_HOST_LENGTH = 256;

    protected final int capacity;
    protected final UnsafeBuffer buffer;

    public Endpoint()
    {
        this(MAX_HOST_LENGTH);
    }

    public Endpoint(final int maxHostLength)
    {
        final int capacity = requiredBufferCapacity(maxHostLength);
        final byte[] byteArray = new byte[capacity];

        this.capacity = capacity;
        this.buffer = new UnsafeBuffer(byteArray);
    }

    public DirectBuffer getBuffer()
    {
        return buffer;
    }

    public Endpoint host(final DirectBuffer src, int offset, final int length)
    {
        final int hostLengthOffset = hostLengthOffset(0);
        buffer.putInt(hostLengthOffset, length);

        final int hostOffset = hostOffset(0);
        buffer.setMemory(hostOffset, capacity - hostOffset, (byte) 0);
        buffer.putBytes(hostOffset, src, offset, length);

        return this;
    }

    public Endpoint host(final byte[] src, final int offset, final int length)
    {
        final int hostLengthOffset = hostLengthOffset(0);
        buffer.putInt(hostLengthOffset, length);

        final int hostOffset = hostOffset(0);
        buffer.setMemory(hostOffset, capacity - hostOffset, (byte) 0);
        buffer.putBytes(hostOffset, src, offset, length);

        return this;
    }

    public Endpoint host(final String host)
    {
        final byte[] hostBytes;
        try
        {
            hostBytes = host.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }

        return host(hostBytes, 0, hostBytes.length);
    }

    public String host()
    {
        final int hostOffset = hostOffset(0);

        final int hostLength = hostLength();
        final byte[] tmp = new byte[hostLength];

        buffer.getBytes(hostOffset, tmp, 0, hostLength);

        final String host;
        try
        {
            host = new String(tmp, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }

        return host;
    }

    public int hostLength()
    {
        final int hostLengthOffset = hostLengthOffset(0);
        return buffer.getInt(hostLengthOffset);
    }

    public int port()
    {
        final int portOffset = portOffset(0);
        return buffer.getInt(portOffset);
    }

    public Endpoint port(final int port)
    {
        final int portOffset = portOffset(0);
        buffer.putInt(portOffset, port);
        return this;
    }

    public void clear()
    {
        buffer.setMemory(0, capacity, (byte) 0);
    }

    public void wrap(final Endpoint endpoint)
    {
        clear();
        endpoint.write(buffer, 0);
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        clear();
        this.buffer.putBytes(0, buffer, offset, length);
    }

    @Override
    public int getLength()
    {
        return EndpointDescriptor.HEADER_OFFSET + hostLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        final int portOffset = portOffset(offset);
        buffer.putInt(portOffset, port());

        final int hostLengthOffset = hostLengthOffset(offset);
        final int hostLength = hostLength();
        buffer.putInt(hostLengthOffset, hostLength);

        final int hostOffset = hostOffset(offset);
        buffer.putBytes(hostOffset, this.buffer, hostOffset(0), hostLength);
    }

    public Endpoint copy()
    {
        final Endpoint copy = new Endpoint();
        copy.wrap(this);
        return copy;
    }

    @Override
    public int compareTo(final Endpoint o)
    {
        final DirectBuffer thisHostBuffer = getBuffer();
        final DirectBuffer thatHostBuffer = o.getBuffer();

        int cmp = thisHostBuffer.compareTo(thatHostBuffer);

        if (cmp == 0)
        {
            cmp = Integer.compare(port(), o.port());
        }

        return cmp;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result +
                ((buffer == null) ? 0 : buffer.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final Endpoint other = (Endpoint) obj;
        if (buffer == null)
        {
            if (other.buffer != null)
            {
                return false;
            }
        }
        else if (!buffer.equals(other.buffer))
        {
            return false;
        }

        return true;
    }

    @Override
    public String toString()
    {
        return "[host: " + host() + ", port: " + port() + "]";
    }

}
