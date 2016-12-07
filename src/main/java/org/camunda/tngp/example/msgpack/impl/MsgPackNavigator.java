package org.camunda.tngp.example.msgpack.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackNavigator
{

    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

    protected int currentPosition;
    protected byte currentFormatByte;
    protected MsgPackFormat currentElementFormat;
    protected int currentElementLength;

    protected UnsafeBuffer valueBuffer = new UnsafeBuffer(0, 0);

    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        this.buffer.wrap(buffer, offset, length);
        moveTo(0);
    }

    public void stepInto()
    {
        // TODO: must be Object or array
        // TODO:
    }

    public void moveTo(int position)
    {
        this.currentPosition = position;

        this.currentFormatByte = buffer.getByte(position);
        this.currentElementFormat = MsgPackFormat.getFormat(currentFormatByte);

        int valueLength;
        switch (currentElementFormat)
        {
            case FIXSTR:
                valueLength = currentFormatByte & 0x1f;
                valueBuffer.wrap(buffer, position + 1, valueLength);
                currentElementLength = valueLength + 1;
                break;
            case STR_8:
                valueLength = buffer.getByte(position + 1) & 0xff;
                valueBuffer.wrap(buffer, position + 2, valueLength);
                currentElementLength = valueLength + 2;
                break;
            case FIXMAP:
                valueLength = currentFormatByte & 0x0f;
            default:
                throw new RuntimeException("unrecognized format");

        }
    }

    // TODO: could behave more iterator-like, i.e. next must be called before first element as well
    public boolean next()
    {
        // TODO: return false if there is no following element
        int nextPosition = currentPosition + currentElementLength;
        if (nextPosition < buffer.capacity())
        {
            moveTo(nextPosition);
            return true;
        }
        else
        {
            return false;
        }
    }

    public int position()
    {
        // TODO: hier müsste man die logische position zurückgeben
        return -1;
    }

    public MsgPackFormat crrentElementFormat()
    {
        return currentElementFormat;
    }

    public boolean matches(JsonPathOperator operator)
    {

        switch (currentElementFormat.getType())
        {
            case STRING:
                return operator.matchesString(this, valueBuffer, 0, valueBuffer.capacity());
            default:
                throw new RuntimeException("unknwon type");
        }

    }
}
