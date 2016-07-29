package org.camunda.tngp.client.cmd;

import org.camunda.tngp.client.ClientCommand;

public interface StartWorkflowInstanceCmd extends ClientCommand<WorkflowInstance>
{
    StartWorkflowInstanceCmd workflowDefinitionId(long id);

    StartWorkflowInstanceCmd workflowDefinitionKey(byte[] key);

    StartWorkflowInstanceCmd workflowDefinitionKey(String key);
}
