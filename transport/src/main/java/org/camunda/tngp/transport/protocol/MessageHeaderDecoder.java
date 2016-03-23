/* Generated SBE (Simple Binary Encoding) message codec */
package org.camunda.tngp.transport.protocol;

import uk.co.real_logic.agrona.DirectBuffer;

@javax.annotation.Generated(value = {"org.camunda.tngp.taskqueue.protocol.MessageHeaderDecoder"})
@SuppressWarnings("all")
public class MessageHeaderDecoder
{
    public static final int ENCODED_LENGTH = 16;
    private DirectBuffer buffer;
    private int offset;

    public MessageHeaderDecoder wrap(final DirectBuffer buffer, final int offset)
    {
        this.buffer = buffer;
        this.offset = offset;
        return this;
    }

    public int encodedLength()
    {
        return ENCODED_LENGTH;
    }

    public static int blockLengthNullValue()
    {
        return 65535;
    }

    public static int blockLengthMinValue()
    {
        return 0;
    }

    public static int blockLengthMaxValue()
    {
        return 65534;
    }


    public int blockLength()
    {
        return (buffer.getShort(offset + 0, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    }


    public static int templateIdNullValue()
    {
        return 65535;
    }

    public static int templateIdMinValue()
    {
        return 0;
    }

    public static int templateIdMaxValue()
    {
        return 65534;
    }


    public int templateId()
    {
        return (buffer.getShort(offset + 2, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    }


    public static int schemaIdNullValue()
    {
        return 65535;
    }

    public static int schemaIdMinValue()
    {
        return 0;
    }

    public static int schemaIdMaxValue()
    {
        return 65534;
    }


    public int schemaId()
    {
        return (buffer.getShort(offset + 4, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    }


    public static int versionNullValue()
    {
        return 65535;
    }

    public static int versionMinValue()
    {
        return 0;
    }

    public static int versionMaxValue()
    {
        return 65534;
    }


    public int version()
    {
        return (buffer.getShort(offset + 6, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    }


    public static long requestIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long requestIdMinValue()
    {
        return 0x0L;
    }

    public static long requestIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }


    public long requestId()
    {
        return buffer.getLong(offset + 8, java.nio.ByteOrder.LITTLE_ENDIAN);
    }

}
