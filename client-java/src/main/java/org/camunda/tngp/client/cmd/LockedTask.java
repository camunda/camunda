package org.camunda.tngp.client.cmd;

public interface LockedTask extends GetPayload
{
    long getId();

    Long getWorkflowInstanceId();
}
