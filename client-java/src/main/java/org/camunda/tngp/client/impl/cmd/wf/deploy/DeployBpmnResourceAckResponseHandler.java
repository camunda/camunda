package org.camunda.tngp.client.impl.cmd.wf.deploy;

import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponseReader;

import org.agrona.DirectBuffer;

public class DeployBpmnResourceAckResponseHandler implements ClientResponseHandler<WorkflowDefinition>
{
    protected DeployBpmnResourceAckResponseReader responseReader = new DeployBpmnResourceAckResponseReader();

    @Override
    public int getResponseSchemaId()
    {
        return responseReader.getSchemaId();
    }

    @Override
    public int getResponseTemplateId()
    {
        return responseReader.getTemplateId();
    }

    @Override
    public WorkflowDefinition readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        responseReader.wrap(responseBuffer, offset, length);
        return new WorkflowDefinitionImpl(responseReader.wfDefinitionId());
    }

    public void setResponseReader(DeployBpmnResourceAckResponseReader responseReader)
    {
        this.responseReader = responseReader;

    }

}
