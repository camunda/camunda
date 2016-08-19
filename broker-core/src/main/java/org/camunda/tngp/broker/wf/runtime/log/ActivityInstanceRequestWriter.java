package org.camunda.tngp.broker.wf.runtime.log;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.taskqueue.data.ActivityInstanceRequestEncoder;

public class ActivityInstanceRequestWriter extends LogEntryWriter<ActivityInstanceRequestWriter, ActivityInstanceRequestEncoder>
{

    protected long key;

    public ActivityInstanceRequestWriter()
    {
        super(new ActivityInstanceRequestEncoder());
    }

    public ActivityInstanceRequestWriter key(long key)
    {
        this.key = key;
        return this;
    }

    @Override
    protected int getBodyLength()
    {
        return ActivityInstanceRequestEncoder.BLOCK_LENGTH;
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key);
    }

}
