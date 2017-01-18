package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.StartWorkflowInstanceCmd;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.RequestWriter;

public class StartWorkflowInstanceCmdImpl extends AbstractCmdImpl<WorkflowInstance>
    implements StartWorkflowInstanceCmd
{

    public StartWorkflowInstanceCmdImpl(final ClientCmdExecutor clientCmdExecutor)
    {
        super(clientCmdExecutor);
    }

    @Override
    public StartWorkflowInstanceCmd workflowDefinitionId(long workflowTypeId)
    {
        return this;
    }

    @Override
    public StartWorkflowInstanceCmd workflowDefinitionKey(byte[] key)
    {
        return this;
    }

    @Override
    public StartWorkflowInstanceCmd workflowDefinitionKey(String key)
    {
        return workflowDefinitionKey(key.getBytes(AbstractCmdImpl.CHARSET));
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return null;
    }

    @Override
    public StartWorkflowInstanceCmd payload(String payload)
    {
        return this;
    }

    @Override
    public ClientResponseHandler<WorkflowInstance> getResponseHandler()
    {
        return new StartWorkflowInstanceResponseHandler();
    }

}
