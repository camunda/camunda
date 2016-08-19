package org.camunda.tngp.broker.taskqueue.log;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceRequestEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceRequestType;
import org.camunda.tngp.util.buffer.BufferWriter;

import org.agrona.MutableDirectBuffer;

public class TaskInstanceRequestWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected TaskInstanceRequestEncoder bodyEncoder = new TaskInstanceRequestEncoder();

    protected long key;
    protected long lockOwnerId;
    protected TaskInstanceRequestType type;
    protected EventSource source;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                TaskInstanceRequestEncoder.BLOCK_LENGTH;
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
            .key(key)
            .lockOwnerId(lockOwnerId)
            .type(type);
    }

    public TaskInstanceRequestWriter type(TaskInstanceRequestType type)
    {
        this.type = type;
        return this;
    }

    public TaskInstanceRequestWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public TaskInstanceRequestWriter lockOwnerId(long lockOwnerId)
    {
        this.lockOwnerId = lockOwnerId;
        return this;
    }

    public TaskInstanceRequestWriter source(EventSource source)
    {
        this.source = source;
        return this;
    }

}
