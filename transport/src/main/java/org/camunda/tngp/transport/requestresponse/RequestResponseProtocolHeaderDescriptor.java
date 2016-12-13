package org.camunda.tngp.transport.requestresponse;

import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.agrona.MutableDirectBuffer;

public class RequestResponseProtocolHeaderDescriptor
{
    public static final int CONNECTION_ID_OFFSET;
    public static final int REQUEST_ID_OFFSET;
    public static final int HEADER_LENGTH;

    static
    {
        int offset = 0;

        CONNECTION_ID_OFFSET = offset;
        offset += SIZE_OF_LONG;

        REQUEST_ID_OFFSET = offset;
        offset += SIZE_OF_LONG;

        HEADER_LENGTH = offset;
    }

    public static int framedLength(int messageLength)
    {
        return HEADER_LENGTH + messageLength;
    }

    public static int headerLength()
    {
        return HEADER_LENGTH;
    }

    public static int connectionIdOffset(int offset)
    {
        return offset + CONNECTION_ID_OFFSET;
    }

    public static int requestIdOffset(int offset)
    {
        return offset + REQUEST_ID_OFFSET;
    }

    protected MutableDirectBuffer buffer;
    protected int offset;

    public RequestResponseProtocolHeaderDescriptor wrap(MutableDirectBuffer buffer, int offset)
    {
        this.buffer = buffer;
        this.offset = offset;
        return this;
    }

    public RequestResponseProtocolHeaderDescriptor connectionId(long connectionId)
    {
        buffer.putLong(connectionIdOffset(offset), connectionId);
        return this;
    }

    public long connectionId()
    {
        return buffer.getLong(connectionIdOffset(offset));
    }

    public RequestResponseProtocolHeaderDescriptor requestId(long requestId)
    {
        buffer.putLong(requestIdOffset(offset), requestId);
        return this;
    }

    public long requestId()
    {
        return buffer.getLong(requestIdOffset(offset));
    }
}
