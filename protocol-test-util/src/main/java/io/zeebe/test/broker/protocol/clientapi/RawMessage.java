package io.zeebe.test.broker.protocol.clientapi;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RawMessage
{

    protected final boolean isResponse;
    protected final UnsafeBuffer message;
    protected final int sequenceNumber;

    public RawMessage(boolean isResponse, int sequenceNumber, DirectBuffer message, int messageOffset, int messageLength)
    {
        this.isResponse = isResponse;
        this.sequenceNumber = sequenceNumber;

        this.message = new UnsafeBuffer(new byte[messageLength]);
        this.message.putBytes(0, message, messageOffset, messageLength);
    }

    public boolean isResponse()
    {
        return isResponse;
    }

    public boolean isMessage()
    {
        return !isResponse;
    }

    /**
     * Determines the order in which messages have been received. Is only meaningful for messages
     * received on the same channel.
     */
    public int getSequenceNumber()
    {
        return sequenceNumber;
    }

    /**
     * @return message excluding transport and protocol header
     */
    public DirectBuffer getMessage()
    {
        return message;
    }
}
