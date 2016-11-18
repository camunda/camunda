package org.camunda.tngp.client.impl.cmd.wf.start;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceEncoder;
import org.camunda.tngp.util.buffer.PayloadRequestWriter;

public class StartWorkflowInstanceRequestWriter implements PayloadRequestWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final StartWorkflowInstanceEncoder requestEncoder = new StartWorkflowInstanceEncoder();

    protected final UnsafeBuffer wfDefinitionKey = new UnsafeBuffer(0, 0);

    protected int resourceId;
    protected int shardId;
    protected long wfDefinitionId = -1;
    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                StartWorkflowInstanceEncoder.BLOCK_LENGTH +
                StartWorkflowInstanceEncoder.wfDefinitionKeyHeaderLength() +
                wfDefinitionKey.capacity() +
                StartWorkflowInstanceEncoder.payloadHeaderLength() +
                payloadBuffer.capacity();
    }

    @Override
    public void write(final MutableDirectBuffer writeBuffer, int writeOffset)
    {
        headerEncoder.wrap(writeBuffer, writeOffset)
            .blockLength(requestEncoder.sbeBlockLength())
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaVersion())
            .resourceId(resourceId)
            .shardId(shardId);

        writeOffset += headerEncoder.encodedLength();

        requestEncoder.wrap(writeBuffer, writeOffset)
            .wfDefinitionId(wfDefinitionId)
            .putWfDefinitionKey(wfDefinitionKey, 0, wfDefinitionKey.capacity())
            .putPayload(payloadBuffer, 0, payloadBuffer.capacity());
    }

    @Override
    public void validate()
    {
        final boolean keySet = wfDefinitionKey.capacity() > 0;
        final boolean idSet = wfDefinitionId >= 0;

        if (keySet && idSet || (!keySet && !idSet))
        {
            throw new RuntimeException("Must set either workflow type id or key");
        }

        if (wfDefinitionKey.capacity() > Constants.WF_DEF_KEY_MAX_LENGTH)
        {
            throw new RuntimeException("Key must not be longer than " + Constants.WF_DEF_KEY_MAX_LENGTH + " bytes");
        }

    }

    public StartWorkflowInstanceRequestWriter resourceId(int resourceId)
    {
        this.resourceId = resourceId;
        return this;
    }

    public StartWorkflowInstanceRequestWriter shardId(int shardId)
    {
        this.shardId = shardId;
        return this;
    }

    public StartWorkflowInstanceRequestWriter wfDefinitionId(long id)
    {
        this.wfDefinitionId = id;
        return this;
    }

    public StartWorkflowInstanceRequestWriter wfDefinitionKey(byte[] bytes)
    {
        wfDefinitionKey.wrap(bytes);
        return this;
    }

    @Override
    public void payload(byte[] bytes, int offset, int length)
    {
        payloadBuffer.wrap(bytes, offset, length);
    }

    @Override
    public void payload(DirectBuffer buffer, int offset, int length)
    {
        payloadBuffer.wrap(buffer, offset, length);
    }

    @Override
    public void payload(ByteBuffer byteBuffer)
    {
        payloadBuffer.wrap(byteBuffer);
    }

}
