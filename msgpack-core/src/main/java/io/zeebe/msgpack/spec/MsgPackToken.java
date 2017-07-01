package io.zeebe.msgpack.spec;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackToken
{

    protected static final int MAX_MAP_ELEMENTS = 0x3fff_ffff;

    protected MsgPackType type;
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

    public int getTotalLength()
    {
        return totalLength;
    }

    public void setTotalLength(int totalLength)
    {
        this.totalLength = totalLength;
    }

    public int getSize()
    {
        return size;
    }

    public MsgPackType getType()
    {
        return type;
    }

    public DirectBuffer getValueBuffer()
    {
        return valueBuffer;
    }

    public void setValue(DirectBuffer buffer, int offset, int length)
    {
        this.valueBuffer.wrap(buffer, offset, length);
    }

    public void setValue(double value)
    {
        this.floatValue = value;
    }

    public void setValue(long value)
    {
        this.integerValue = value;
    }

    public void setValue(boolean value)
    {
        this.booleanValue = value;
    }

    public void setMapHeader(int size)
    {
        this.size = size;
    }

    public void setArrayHeader(int size)
    {
        this.size = size;
    }


    public void setType(MsgPackType type)
    {
        this.type = type;
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
