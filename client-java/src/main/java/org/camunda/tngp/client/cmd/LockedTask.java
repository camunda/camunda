package org.camunda.tngp.client.cmd;

import java.time.Instant;

public interface LockedTask
{
    long getId();

    Long getWorkflowInstanceId();

    Instant getLockTime();

    String getPayloadString();
}
