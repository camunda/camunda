package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import org.agrona.MutableDirectBuffer;

public class SingleTaskAckResponseWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected SingleTaskAckEncoder bodyEncoder = new SingleTaskAckEncoder();

    protected long taskId;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                SingleTaskAckEncoder.BLOCK_LENGTH;
    }

    public SingleTaskAckResponseWriter taskId(long taskId)
    {
        this.taskId = taskId;
        return this;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(0)
            .shardId(0)
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            .templateId(bodyEncoder.sbeTemplateId());

        bodyEncoder
            .wrap(buffer, offset + headerEncoder.encodedLength())
            .taskId(taskId);
    }

}
