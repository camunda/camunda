package org.camunda.tngp.broker.wf.runtime.log;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.taskqueue.data.ActivityInstanceRequestEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class ActivityInstanceRequestWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected ActivityInstanceRequestEncoder bodyEncoder = new ActivityInstanceRequestEncoder();

    protected EventSource source;
    protected long key;

    public ActivityInstanceRequestWriter source(EventSource source)
    {
        this.source = source;
        return this;
    }

    public ActivityInstanceRequestWriter key(long key)
    {
        this.key = key;
        return this;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ActivityInstanceRequestEncoder.BLOCK_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(0)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(0)
            .source(source.value())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .key(key);

    }

}
