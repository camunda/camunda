package io.zeebe.transport.requestresponse;

import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

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

    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[HEADER_LENGTH]);

    public RequestResponseProtocolHeaderDescriptor wrap(DirectBuffer buffer, int offset)
    {
        this.buffer.wrap(buffer, offset, HEADER_LENGTH);
        return this;
    }

    public RequestResponseProtocolHeaderDescriptor connectionId(long connectionId)
    {
        buffer.putLong(CONNECTION_ID_OFFSET, connectionId);
        return this;
    }

    public long connectionId()
    {
        return buffer.getLong(CONNECTION_ID_OFFSET);
    }

    public RequestResponseProtocolHeaderDescriptor requestId(long requestId)
    {
        buffer.putLong(REQUEST_ID_OFFSET, requestId);
        return this;
    }

    public long requestId()
    {
        return buffer.getLong(REQUEST_ID_OFFSET);
    }
}
