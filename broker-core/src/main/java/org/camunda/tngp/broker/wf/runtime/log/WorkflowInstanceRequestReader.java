package org.camunda.tngp.broker.wf.runtime.log;

import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.ProcessInstanceRequestType;
import org.camunda.tngp.taskqueue.data.WorkflowInstanceRequestDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected WorkflowInstanceRequestDecoder bodyDecoder = new WorkflowInstanceRequestDecoder();

    protected UnsafeBuffer wfDefinitionKeyBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();
        offset += WorkflowInstanceRequestDecoder.wfDefinitionKeyHeaderLength();

        final int wfDefinitionKeyLength = bodyDecoder.wfDefinitionKeyLength();
        if (wfDefinitionKeyLength > 0)
        {
            wfDefinitionKeyBuffer.wrap(buffer, offset, bodyDecoder.wfDefinitionKeyLength());
        }
        else
        {
            wfDefinitionKeyBuffer.wrap(0L, 0);
        }

    }

    public long wfDefinitionId()
    {
        return bodyDecoder.wfDefinitionId();
    }

    public DirectBuffer wfDefinitionKey()
    {
        return wfDefinitionKeyBuffer;
    }

    public ProcessInstanceRequestType type()
    {
        return bodyDecoder.type();
    }

}
