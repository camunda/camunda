package org.camunda.tngp.client.cmd;

public interface StartWorkflowInstanceCmd extends SetPayloadCmd<WorkflowInstance, StartWorkflowInstanceCmd>
{
    StartWorkflowInstanceCmd workflowDefinitionId(long id);

    StartWorkflowInstanceCmd workflowDefinitionKey(byte[] key);

    StartWorkflowInstanceCmd workflowDefinitionKey(String key);
}
