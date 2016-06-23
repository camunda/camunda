package org.camunda.tngp.transport.requestresponse;

import static uk.co.real_logic.agrona.BitUtil.*;

public class TransportRequestHeaderDescriptor
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
}
