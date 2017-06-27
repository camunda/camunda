package io.zeebe.broker.task;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

import io.zeebe.list.CompactList;
import io.zeebe.util.buffer.BufferReader;

public class CreditsRequest implements BufferReader
{
    protected static final int LENGTH = BitUtil.SIZE_OF_LONG + BitUtil.SIZE_OF_INT;
    protected static final int TYPE = 42;

    protected UnsafeBuffer content = new UnsafeBuffer(new byte[LENGTH]);

    public CreditsRequest()
    {
    }

    public CreditsRequest(long subscriberKey, int credits)
    {
        setSubscriberKey(subscriberKey);
        setCredits(credits);
    }

    @Override
    public void wrap(DirectBuffer buffer, int index, int length)
    {
        if (length != LENGTH)
        {
            throw new RuntimeException("Unexpected message length");
        }

        this.content.putBytes(0, buffer, index, length);

    }

    public long getSubscriberKey()
    {
        return content.getLong(0);
    }

    public void setSubscriberKey(long subscriberKey)
    {
        this.content.putLong(0, subscriberKey);
    }

    public int getCredits()
    {
        return content.getInt(BitUtil.SIZE_OF_LONG);
    }

    public void setCredits(int credits)
    {
        this.content.putInt(BitUtil.SIZE_OF_LONG, credits);
    }

    /**
     * @param ringBuffer
     * @return true if success
     */
    public boolean writeTo(OneToOneRingBuffer ringBuffer)
    {
        return ringBuffer.write(TYPE, content, 0, LENGTH);
    }

    public void appendTo(CompactList list)
    {
        list.add(content);
    }

    public void wrapListElement(CompactList list, int index)
    {
        list.wrap(index, content);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof CreditsRequest) || obj == null)
        {
            return false;
        }

        final CreditsRequest request = (CreditsRequest) obj;

        return request.content.equals(this.content);
    }

    @Override
    public int hashCode()
    {
        return content.hashCode();
    }
}
