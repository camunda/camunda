package org.camunda.tngp.transport.protocol;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;

public class TransportHeaderDescriptor
{

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

    protected MutableDirectBuffer buffer;
    protected int offset;

    public TransportHeaderDescriptor wrap(MutableDirectBuffer buffer, int offset)
    {
        this.buffer = buffer;
        this.offset = offset;
        return this;
    }

    public TransportHeaderDescriptor protocolId(short protocolId)
    {
        buffer.putShort(offset, protocolId);
        return this;
    }

}
