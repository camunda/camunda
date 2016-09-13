package org.camunda.tngp.client.task;

import java.util.Date;

/**
 * Represents a task that was received by a subscription.
 *
 * @author Lindhauer
 */
public interface Task extends WaitStateResponse
{

    /**
     * @return the task's id
     */
    long getId();

    /**
     * @return the id of the workflow instance this task belongs to. May be <code>null</code> if this
     *   is a standalone task.
     */
    Long getWorkflowInstanceId();

    /**
     * @return the task's type
     */
    String getType();

    /**
     * @return the time until when the task is locked
     *   and can be exclusively processed by this client.
     */
    Date getLockExpirationTime();
}
