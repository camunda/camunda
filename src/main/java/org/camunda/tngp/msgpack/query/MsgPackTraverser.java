package org.camunda.tngp.msgpack.query;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackToken;

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
            final int nextElementPosition = currentPosition;
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

        currentValue.wrap(buffer, currentPosition);
        currentPosition += currentValue.getTotalLength();
    }

    protected boolean hasNext()
    {
        return currentPosition < buffer.capacity();
    }




}
