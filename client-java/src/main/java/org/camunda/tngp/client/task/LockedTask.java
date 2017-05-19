package org.camunda.tngp.client.task;

import java.time.Instant;

public interface LockedTask
{
    long getId();

    Long getWorkflowInstanceKey();

    Instant getLockTime();

    String getPayloadString();
}
