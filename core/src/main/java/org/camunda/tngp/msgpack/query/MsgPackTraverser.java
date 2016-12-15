package org.camunda.tngp.msgpack.query;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackToken;

public class MsgPackTraverser
{

    protected static final int NO_INVALID_POSITION = -1;

    protected int invalidPosition;
    protected String errorMessage;

    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
    protected int currentPosition;

    protected MsgPackToken currentValue = new MsgPackToken();

    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        this.buffer.wrap(buffer, offset, length);
        this.currentPosition = 0;
        this.invalidPosition = NO_INVALID_POSITION;
        this.errorMessage = null;
    }

    public MsgPackToken getCurrentElement()
    {
        return currentValue;
    }

    /**
     * @param visitor
     * @return true if document could be traversed successfully
     */
    public boolean traverse(MsgPackTokenVisitor visitor)
    {
        while (hasNext())
        {
            final int nextElementPosition = currentPosition;
            final boolean success = wrapNextElement();

            if (!success)
            {
                return false;
            }

            visitor.visitElement(nextElementPosition, currentValue);
        }

        return true;
    }

    public int getInvalidPosition()
    {
        return invalidPosition;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    protected boolean wrapNextElement()
    {
        final boolean success = currentValue.wrap(buffer, currentPosition);
        if (success)
        {
            currentPosition += currentValue.getTotalLength();
        }
        {
            invalidPosition = currentPosition;
            errorMessage = currentValue.getError();
        }

        return success;


    }

    protected boolean hasNext()
    {
        return currentPosition < buffer.capacity();
    }




}
