package org.camunda.tngp.client.workflow.cmd;

/**
 * Represents the exception, which will be thrown if the creation of the workflow instantiation is rejected.
 */
public class WorkflowInstanceRejectedException extends RuntimeException
{
    private static final String EXCEPTION_MSG = "Creation of workflow instance with id %s and version %d was rejected.";

    public WorkflowInstanceRejectedException(String bpmnProcessId, int version)
    {
        super(String.format(EXCEPTION_MSG, bpmnProcessId, version));
    }
}
