package io.zeebe.client.workflow.cmd;

import io.zeebe.client.cmd.SetPayloadCmd;

/**
 * Represents an command to create a workflow instance.
 */
public interface CreateWorkflowInstanceCmd extends SetPayloadCmd<WorkflowInstance, CreateWorkflowInstanceCmd>
{
    /**
     * Represents the latest version of a deployed workflow definition.
      */
    int LATEST_VERSION = -1;

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
     * If the version is set to {@link #LATEST_VERSION}, the latest version of the deployed workflow definition is used.
     *
     * @param version the version of the workflow definition
     * @return the current create command
     */
    CreateWorkflowInstanceCmd version(int version);

    /**
     * Sets the version, which corresponds to the deployed workflow definition, to latest.
     *
     * @see {@link #version(int)}
     * @return the current create command
     */
    CreateWorkflowInstanceCmd latestVersion();
}
