package org.camunda.tngp.example.msgpack.impl.newidea;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.MsgPackFormat;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

public class MsgPackToken
{

    protected MsgPackFormat format;
    protected int totalLength;

    // string
    protected UnsafeBuffer valueBuffer = new UnsafeBuffer(0, 0);

    // map/array
    protected int size;

    public void wrap(DirectBuffer buffer, int valueOffset, MsgPackFormat format)
    {
        this.format = format;

        int valueLength;
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
            case FIXMAP:
                totalLength = 1; // only counting header
                size = (buffer.getByte(valueOffset) & 0x0f) * 2;
                break;
            case FIXARR:
                totalLength = 1; // only counting header
                size = buffer.getByte(valueOffset) & 0x0f;
                break;
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


}
