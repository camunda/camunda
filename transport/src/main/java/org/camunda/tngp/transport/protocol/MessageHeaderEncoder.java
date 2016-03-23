/* Generated SBE (Simple Binary Encoding) message codec */
package org.camunda.tngp.transport.protocol;

import uk.co.real_logic.agrona.MutableDirectBuffer;

@javax.annotation.Generated(value = {"org.camunda.tngp.taskqueue.protocol.MessageHeaderEncoder"})
@SuppressWarnings("all")
public class MessageHeaderEncoder
{
    public static final int ENCODED_LENGTH = 16;
    private MutableDirectBuffer buffer;
    private int offset;

    public MessageHeaderEncoder wrap(final MutableDirectBuffer buffer, final int offset)
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

    public MessageHeaderEncoder blockLength(final int value)
    {
        buffer.putShort(offset + 0, (short)value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public MessageHeaderEncoder templateId(final int value)
    {
        buffer.putShort(offset + 2, (short)value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public MessageHeaderEncoder schemaId(final int value)
    {
        buffer.putShort(offset + 4, (short)value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public MessageHeaderEncoder version(final int value)
    {
        buffer.putShort(offset + 6, (short)value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public MessageHeaderEncoder requestId(final long value)
    {
        buffer.putLong(offset + 8, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }

}
