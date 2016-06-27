package org.camunda.tngp.client.impl.cmd.wf.start;

import org.camunda.tngp.client.impl.cmd.ClientRequestWriter;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceEncoder;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceRequestWriter implements ClientRequestWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final StartWorkflowInstanceEncoder requestEncoder = new StartWorkflowInstanceEncoder();

    protected final UnsafeBuffer wfTypeKey = new UnsafeBuffer(0, 0);

    protected int resourceId;
    protected int shardId;
    protected long wfTypeId = -1;

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                StartWorkflowInstanceEncoder.BLOCK_LENGTH +
                StartWorkflowInstanceEncoder.wfTypeKeyHeaderLength() +
                wfTypeKey.capacity();
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
            .wfTypeId(wfTypeId)
            .putWfTypeKey(wfTypeKey, 0, wfTypeKey.capacity());
    }

    @Override
    public void validate()
    {
        final boolean keySet = wfTypeKey.capacity() > 0;
        final boolean idSet = wfTypeId >= 0;

        if (keySet && idSet || (!keySet && !idSet))
        {
            throw new RuntimeException("Must set either workflow type id or key");
        }

        if (wfTypeKey.capacity() > Constants.WF_TYPE_KEY_MAX_LENGTH)
        {
            throw new RuntimeException("Key must not be longer than " + Constants.WF_TYPE_KEY_MAX_LENGTH + " bytes");
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

    public StartWorkflowInstanceRequestWriter wfTypeId(long id)
    {
        this.wfTypeId = id;
        return this;
    }

    public StartWorkflowInstanceRequestWriter wfTypeKey(byte[] bytes)
    {
        wfTypeKey.wrap(bytes);
        return this;
    }


}
