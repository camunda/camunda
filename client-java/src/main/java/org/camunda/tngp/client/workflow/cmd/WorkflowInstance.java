package org.camunda.tngp.client.workflow.cmd;

/**
 * Represents a workflow instance, which has been created.
 */
public interface WorkflowInstance
{
    /**
     * The bpmn process id which corresponds to the workflow definition id.
     * @return
     */
    String getBpmnProcessId();

    /**
     * The workflow instance key which identifies the workflow instance.
     * @return the workflow instance key
     */
    long getWorkflowInstanceKey();

    /**
     * The version of the workflow definition, which is used to created this instance,
     * @return the version of the workflow definition
     */
    int getVersion();
}
