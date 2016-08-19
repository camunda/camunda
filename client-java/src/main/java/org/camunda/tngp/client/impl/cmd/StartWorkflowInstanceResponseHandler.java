package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceResponseReader;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceResponseDecoder;

import org.agrona.DirectBuffer;

public class StartWorkflowInstanceResponseHandler implements ClientResponseHandler<WorkflowInstance>
{
    protected StartWorkflowInstanceResponseReader responseReader = new StartWorkflowInstanceResponseReader();

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
        responseReader.wrap(responseBuffer, offset, length);

        return new WorkflowInstanceImpl(responseReader.wfInstanceId());
    }

    public void setResponseReader(StartWorkflowInstanceResponseReader responseReader)
    {
        this.responseReader = responseReader;
    }

}
