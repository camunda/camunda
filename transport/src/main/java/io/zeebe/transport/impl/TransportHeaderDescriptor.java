package io.zeebe.transport.impl;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TransportHeaderDescriptor
{
    public static final short REQUEST_RESPONSE = 0;
    public static final short FULL_DUPLEX_SINGLE_MESSAGE = 1;

    public static final int PROTOCOL_ID_OFFSET;
    public static final int HEADER_LENGTH;

    static
    {
        int offset = 0;

        PROTOCOL_ID_OFFSET = offset;
        offset += BitUtil.SIZE_OF_SHORT;

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

    public static int protocolIdOffset(int offset)
    {
        return offset + PROTOCOL_ID_OFFSET;
    }

    protected final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

    public TransportHeaderDescriptor wrap(DirectBuffer buffer, int offset)
    {
        this.buffer.wrap(buffer, offset, HEADER_LENGTH);
        return this;
    }

    public TransportHeaderDescriptor protocolId(short protocolId)
    {
        buffer.putShort(PROTOCOL_ID_OFFSET, protocolId);
        return this;
    }

    public TransportHeaderDescriptor putProtocolSingleMessage()
    {
        return protocolId(FULL_DUPLEX_SINGLE_MESSAGE);
    }

    public TransportHeaderDescriptor putProtocolRequestReponse()
    {
        return protocolId(REQUEST_RESPONSE);
    }

    public int protocolId()
    {
        return buffer.getShort(PROTOCOL_ID_OFFSET);
    }

}
