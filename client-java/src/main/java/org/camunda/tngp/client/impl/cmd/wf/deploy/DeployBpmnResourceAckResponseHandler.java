package org.camunda.tngp.client.impl.cmd.wf.deploy;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;

public class DeployBpmnResourceAckResponseHandler implements ClientResponseHandler<WorkflowDefinition>
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
    public WorkflowDefinition readResponse(DirectBuffer responseBuffer, int offset, int blockLength, int version)
    {
        return null;
    }
}
