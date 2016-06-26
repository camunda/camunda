package org.camunda.tngp.client.impl.cmd.wf.deploy;

import org.camunda.tngp.client.cmd.DeployedWorkflowType;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponseReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class DeployBpmnResourceAckResponseHandler implements ClientResponseHandler<DeployedWorkflowType>
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
    public DeployedWorkflowType readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        responseReader.wrap(responseBuffer, offset, length);
        return new DeployedWorkflowTypeImpl(responseReader.wfTypeId());
    }

    public void setResponseReader(DeployBpmnResourceAckResponseReader responseReader)
    {
        this.responseReader = responseReader;

    }

}
