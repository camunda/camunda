package org.camunda.tngp.broker.taskqueue.processor.stuff;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class EncodedStuff
{
    protected MutableDirectBuffer encodeBuffer = new UnsafeBuffer(0, 0);
    protected int encodedLength = 0;

    public void encode(EncodableDataStuff dataStuff)
    {
        final int length = dataStuff.getEncodedLength();

        if (encodeBuffer.capacity() < length)
        {
            encodeBuffer.wrap(new byte[length]);
        }

        dataStuff.encode(encodeBuffer, 0);

        encodedLength = length;
    }

    public DirectBuffer getBuffer()
    {
        return encodeBuffer;
    }

    public int getEncodedLength()
    {
        return encodedLength;
    }
}
