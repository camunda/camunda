package org.camunda.tngp.broker.wf.runtime.log;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.protocol.log.ActivityInstanceRequestEncoder;

public class ActivityInstanceRequestWriter extends LogEntryWriter<ActivityInstanceRequestWriter, ActivityInstanceRequestEncoder>
{

    protected long key;
    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    public ActivityInstanceRequestWriter()
    {
        super(new ActivityInstanceRequestEncoder());
    }

    public ActivityInstanceRequestWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public ActivityInstanceRequestWriter payload(DirectBuffer payload, int offset, int length)
    {
        this.payloadBuffer.wrap(payload, offset, length);
        return this;
    }

    @Override
    protected int getBodyLength()
    {
        return ActivityInstanceRequestEncoder.BLOCK_LENGTH +
                ActivityInstanceRequestEncoder.payloadHeaderLength() +
                payloadBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key)
            .putPayload(payloadBuffer, 0, payloadBuffer.capacity());
    }

}
