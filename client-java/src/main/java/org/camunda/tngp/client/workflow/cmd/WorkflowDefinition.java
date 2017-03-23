package org.camunda.tngp.client.workflow.cmd;


public interface WorkflowDefinition
{
    String getBpmnProcessId();

    int getVersion();
}
