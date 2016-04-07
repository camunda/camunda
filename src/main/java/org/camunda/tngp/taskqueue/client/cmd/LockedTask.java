package org.camunda.tngp.taskqueue.client.cmd;

public interface LockedTask extends GetPayload
{
    long getId();
}
