package org.camunda.tngp.broker.clustering.channel;

import java.io.UnsupportedEncodingException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class Endpoint implements Comparable<Endpoint>
{
    public static final int MAX_HOST_LENGTH = 128;

    protected final UnsafeBuffer hostBuffer;
    protected int hostLength;
    protected int port;

    public Endpoint()
    {
        this(MAX_HOST_LENGTH);
    }

    public Endpoint(final int maxHostLength)
    {
        final byte[] byteArray = new byte[maxHostLength];
        this.hostBuffer = new UnsafeBuffer(byteArray);
    }

    public MutableDirectBuffer getHostBuffer()
    {
        return hostBuffer;
    }

    public Endpoint host(final DirectBuffer src, int offset, final int length)
    {
        hostBuffer.putBytes(0, src, offset, length);
        hostBuffer.setMemory(length, hostBuffer.capacity() - length, (byte) 0);
        hostLength = length;
        return this;
    }

    public Endpoint host(final byte[] src, final int offset, final int length)
    {
        hostBuffer.putBytes(0, src, offset, length);
        hostBuffer.setMemory(length, hostBuffer.capacity() - length, (byte) 0);
        hostLength = length;
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
        final int hostLength = hostLength();
        final byte[] tmp = new byte[hostLength];
        hostBuffer.getBytes(0, tmp, 0, hostLength);

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
        return hostLength;
    }

    public Endpoint hostLength(final int hostLength)
    {
        this.hostLength = hostLength;
        return this;
    }

    public int port()
    {
        return port;
    }

    public Endpoint port(final int port)
    {
        this.port = port;
        return this;
    }

    public void reset()
    {
        final int capacity = hostBuffer.capacity();
        hostBuffer.setMemory(0, capacity, (byte) 0);
        hostLength = 0;
        port = 0;
    }

    public void wrap(final Endpoint endpoint)
    {
        reset();
        host(endpoint.getHostBuffer(), 0, endpoint.hostLength());
        port(endpoint.port());
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
        final DirectBuffer thisHostBuffer = getHostBuffer();
        final DirectBuffer thatHostBuffer = o.getHostBuffer();

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
        result = prime * result + ((hostBuffer == null) ? 0 : hostBuffer.hashCode());
        result = prime * result + port;
        return result;
    }

    @Override
    public boolean equals(Object obj)
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
        if (port != other.port)
        {
            return false;
        }
        if (hostBuffer == null)
        {
            if (other.hostBuffer != null)
            {
                return false;
            }
        }
        else if (!hostBuffer.equals(other.hostBuffer))
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
