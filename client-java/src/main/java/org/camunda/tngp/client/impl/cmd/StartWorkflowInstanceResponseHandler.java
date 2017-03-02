package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.cmd.WorkflowInstance;

public class StartWorkflowInstanceResponseHandler implements ClientResponseHandler<WorkflowInstance>
{

    @Override
    public int getResponseSchemaId()
    {
        return -1;
    }

    @Override
    public int getResponseTemplateId()
    {
        return -1;
    }

    @Override
    public WorkflowInstance readResponse(DirectBuffer responseBuffer, int offset, int blockLength, int version)
    {
        return null;
    }

}
