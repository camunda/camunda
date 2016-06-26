package org.camunda.tngp.client.cmd;

import org.camunda.tngp.client.ClientCommand;

public interface StartWorkflowInstanceCmd extends ClientCommand<WorkflowInstance>
{
    StartWorkflowInstanceCmd workflowTypeId(long workflowTypeId);

    StartWorkflowInstanceCmd workflowTypeKey(byte[] key);

    StartWorkflowInstanceCmd workflowTypeKey(String key);
}
