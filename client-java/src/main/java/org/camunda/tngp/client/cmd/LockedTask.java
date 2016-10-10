package org.camunda.tngp.client.cmd;

import java.time.Instant;

public interface LockedTask extends GetPayload
{
    long getId();

    Long getWorkflowInstanceId();

    Instant getLockTime();
}
