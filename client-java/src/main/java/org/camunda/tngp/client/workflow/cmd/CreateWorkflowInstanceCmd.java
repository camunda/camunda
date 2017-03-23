package org.camunda.tngp.client.workflow.cmd;

import org.camunda.tngp.client.cmd.SetPayloadCmd;

/**
 * Represents an command to create a workflow instance.
 */
public interface CreateWorkflowInstanceCmd extends SetPayloadCmd<WorkflowInstance, CreateWorkflowInstanceCmd>
{
    /**
     * Sets the BPMN process id, which identifies the workflow definition.
     *
     * @param id the id which identifies the workflow definition
     * @return the current create command
     */
    CreateWorkflowInstanceCmd bpmnProcessId(String id);

    /**
     * Sets the version, which corresponds to the deployed workflow definition.
     *
     * @param version the version of the workflow definition
     * @return the current create command
     */
    CreateWorkflowInstanceCmd version(int version);
}
