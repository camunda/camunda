package org.camunda.tngp.client.workflow.cmd;

/**
 * Represents a workflow instance, which has been created.
 */
public interface WorkflowInstance
{
    /**
     * The BPMN process id which identifies the workflow definition.
     * @return the id of the BPMN process
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
