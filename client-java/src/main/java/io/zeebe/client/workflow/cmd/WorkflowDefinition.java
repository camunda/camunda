package io.zeebe.client.workflow.cmd;


public interface WorkflowDefinition
{
    String getBpmnProcessId();

    int getVersion();
}
