package org.camunda.tngp.example.msgpack.impl.newidea;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.MsgPackFormat;

public class MsgPackTraverser
{

    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
    protected int currentPosition;

    protected MsgPackToken currentValue = new MsgPackToken();

    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        this.buffer.wrap(buffer, offset, length);
        this.currentPosition = 0;
    }

    public MsgPackToken getCurrentElement()
    {
        return currentValue;
    }

    public void traverse(MsgPackTokenVisitor visitor)
    {
        while (hasNext())
        {
            int nextElementPosition = currentPosition;
            wrapNextElement();
            visitor.visitElement(nextElementPosition, currentValue);
        }
    }

    protected void wrapNextElement()
    {
        if (!hasNext())
        {
            throw new RuntimeException("no next element");
        }

        byte currentFormatByte = buffer.getByte(currentPosition);
        MsgPackFormat currentElementFormat = MsgPackFormat.getFormat(currentFormatByte);

        currentValue.wrap(buffer, currentPosition, currentElementFormat);
        currentPosition += currentValue.getTotalLength();
    }

    protected boolean hasNext()
    {
        return currentPosition < buffer.capacity();
    }




}
