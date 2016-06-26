package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceResponseDecoder;

import uk.co.real_logic.agrona.DirectBuffer;

public class StartWorkflowInstanceResponseHandler implements ClientResponseHandler<WorkflowInstance>
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected StartWorkflowInstanceResponseDecoder responseDecoder = new StartWorkflowInstanceResponseDecoder();

    @Override
    public int getResponseSchemaId()
    {
        return StartWorkflowInstanceResponseDecoder.SCHEMA_ID;
    }

    @Override
    public int getResponseTemplateId()
    {
        return StartWorkflowInstanceResponseDecoder.TEMPLATE_ID;
    }

    @Override
    public WorkflowInstance readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        headerDecoder.wrap(responseBuffer, offset);
        offset += headerDecoder.encodedLength();

        responseDecoder.wrap(responseBuffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        return new WorkflowInstanceImpl(responseDecoder.wfInstanceId());
    }

}
