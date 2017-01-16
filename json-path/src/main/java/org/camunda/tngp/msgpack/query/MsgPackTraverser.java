package org.camunda.tngp.msgpack.query;

import org.agrona.DirectBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackToken;

public class MsgPackTraverser
{

    protected static final int NO_INVALID_POSITION = -1;

    protected String errorMessage;
    protected int invalidPosition;

    protected MsgPackReader msgPackReader = new MsgPackReader();

    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        this.msgPackReader.wrap(buffer, offset, length);
        this.invalidPosition = NO_INVALID_POSITION;
        this.errorMessage = null;
    }

    /**
     * @param visitor
     * @return true if document could be traversed successfully
     */
    public boolean traverse(MsgPackTokenVisitor visitor)
    {
        while (msgPackReader.hasNext())
        {
            final int nextTokenPosition = msgPackReader.getOffset();

            final MsgPackToken nextToken;
            try
            {
                nextToken = msgPackReader.readToken();
            }
            catch (Exception e)
            {
                errorMessage = e.getMessage();
                invalidPosition = nextTokenPosition;
                return false;
            }

            visitor.visitElement(nextTokenPosition, nextToken);
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

}
