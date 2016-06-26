package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.StartWorkflowInstanceCmd;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.wf.start.StartWorkflowInstanceRequestWriter;

public class StartWorkflowInstanceCmdImpl extends AbstractCmdImpl<WorkflowInstance>
    implements StartWorkflowInstanceCmd
{
    protected final StartWorkflowInstanceRequestWriter requestWriter = new StartWorkflowInstanceRequestWriter();

    public StartWorkflowInstanceCmdImpl(final ClientCmdExecutor clientCmdExecutor)
    {
        super(clientCmdExecutor, new StartWorkflowInstanceResponseHandler());
    }

    @Override
    public StartWorkflowInstanceCmd workflowTypeId(long workflowTypeId)
    {
        requestWriter.wfTypeId(workflowTypeId);
        return this;
    }

    @Override
    public StartWorkflowInstanceCmd workflowTypeKey(byte[] key)
    {
        requestWriter.getWfTypeKey().wrap(key);
        return this;
    }

    @Override
    public StartWorkflowInstanceCmd workflowTypeKey(String key)
    {
        return workflowTypeKey(key.getBytes(CHARSET));
    }

    @Override
    public ClientRequestWriter getRequestWriter()
    {
        return requestWriter;
    }

}
