package org.camunda.tngp.msgpack.spec;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackToken
{

    protected static final int MAX_MAP_ELEMENTS = 0x3fff_ffff;

    protected MsgPackFormat format;
    protected int totalLength;

    // string
    protected UnsafeBuffer valueBuffer = new UnsafeBuffer(0, 0);

    // boolean
    protected boolean booleanValue;

    // map/array
    protected int size;

    // int
    protected long integerValue;

    // float32/float64
    protected double floatValue;

    /*
     * strictfp modifier to ensure no information is lost when converting 32-bit float to double,
     * see https://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.1.2
     */
    public strictfp void wrap(DirectBuffer buffer, int valueOffset)
    {
        final byte currentFormatByte = buffer.getByte(valueOffset);
        this.format = MsgPackFormat.getFormat(currentFormatByte);

        final int valueLength;
        switch (format)
        {
            case FIXSTR:
                valueLength = buffer.getByte(valueOffset) & 0x1f;
                valueBuffer.wrap(buffer, valueOffset + 1, valueLength);
                totalLength = valueLength + 1;
                break;
            case STR_8:
                valueLength = buffer.getByte(valueOffset + 1) & 0xff;
                valueBuffer.wrap(buffer, valueOffset + 2, valueLength);
                totalLength = valueLength + 2;
                break;
            case STR_16:
                valueLength = buffer.getShort(valueOffset + 1, ByteOrder.BIG_ENDIAN) & 0xff;
                valueBuffer.wrap(buffer, valueOffset + 3, valueLength);
                totalLength = valueLength + 3;
                break;
            case STR_32:
                valueLength = buffer.getInt(valueOffset + 1, ByteOrder.BIG_ENDIAN);
                if (valueLength < 0)
                {
                    throw new RuntimeException("maximum String length supported is 2**31 bytes");
                }
                valueBuffer.wrap(buffer, valueOffset + 5, valueLength);
                totalLength = valueLength + 5;
                break;
            case FIXMAP:
                totalLength = 1; // only counting header
                size = ((buffer.getByte(valueOffset) & 0x0f) << 1);
                break;
            case MAP_16:
                totalLength = 3; // only counting header
                size = (buffer.getShort(valueOffset + 1, ByteOrder.BIG_ENDIAN) & 0xff) << 1;
                break;
            case MAP_32:
                totalLength = 5; // only counting header
                final int numElements = buffer.getInt(valueOffset + 1, ByteOrder.BIG_ENDIAN);
                if (numElements < 0 || numElements > MAX_MAP_ELEMENTS)
                {
                    throw new RuntimeException("no more than 2**30 map entries supported");
                }
                size = numElements << 1;
                break;
            case FIXARR:
                totalLength = 1; // only counting header
                size = buffer.getByte(valueOffset) & 0x0f;
                break;
            case ARRAY_16:
                totalLength = 3; // only counting header
                size = buffer.getShort(valueOffset + 1, ByteOrder.BIG_ENDIAN) & 0xff;
                break;
            case ARRAY_32:
                totalLength = 5; // only counting header
                size = buffer.getInt(valueOffset + 1, ByteOrder.BIG_ENDIAN);
                if (size < 0)
                {
                    throw new RuntimeException("no more than 2**31 array elements supported");
                }
                break;
            case NIL:
                totalLength = 1;
                break;
            case BOOLEAN_TRUE:
                totalLength = 1;
                booleanValue = true;
                break;
            case BOOLEAN_FALSE:
                totalLength = 1;
                booleanValue = false;
                break;
            case FIXNUM_POSITIVE:
            case FIXNUM_NEGATIVE:
                totalLength = 1;
                integerValue = buffer.getByte(valueOffset);
                break;
            case UINT_8:
                totalLength = 2;
                integerValue = buffer.getByte(valueOffset + 1) & 0xff;
                break;
            case UINT_16:
                totalLength = 3;
                integerValue = buffer.getShort(valueOffset + 1, ByteOrder.BIG_ENDIAN) & 0xffff;
                break;
            case UINT_32:
                totalLength = 5;
                integerValue = ((long) buffer.getInt(valueOffset + 1, ByteOrder.BIG_ENDIAN))
                        & 0xffff_ffffL;
                break;
            case UINT_64:
                totalLength = 9;
                integerValue = buffer.getLong(valueOffset + 1, ByteOrder.BIG_ENDIAN);
                break;
            case FLOAT_32:
                totalLength = 5;
                floatValue = buffer.getFloat(valueOffset + 1, ByteOrder.BIG_ENDIAN);
                return;
            case FLOAT_64:
                totalLength = 9;
                floatValue = buffer.getDouble(valueOffset + 1, ByteOrder.BIG_ENDIAN);
                return;
            default:
                throw new RuntimeException("Unknown format");
        }
    }

    public DirectBuffer valueBuffer()
    {
        return valueBuffer();
    }

    public MsgPackType type()
    {
        return format.getType();
    }

    public int getTotalLength()
    {
        return totalLength;
    }

    public int getSize()
    {
        return size;
    }

    public MsgPackFormat getFormat()
    {
        return format;
    }

    public MsgPackType getType()
    {
        return format.getType();
    }

    public DirectBuffer getValueBuffer()
    {
        return valueBuffer;
    }

    public boolean getBooleanValue()
    {
        return booleanValue;
    }

    /**
     * when using this method, keep the value's format in mind;
     * values of negative fixnum (signed) and unsigned integer can return
     * the same long value while representing different numbers
     */
    public long getIntegerValue()
    {
        return integerValue;
    }

    public double getFloatValue()
    {
        return floatValue;
    }

}
