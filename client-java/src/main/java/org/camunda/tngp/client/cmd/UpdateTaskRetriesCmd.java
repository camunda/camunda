package org.camunda.tngp.client.cmd;

import java.util.Map;

public interface UpdateTaskRetriesCmd extends SetPayloadCmd<Long, UpdateTaskRetriesCmd>
{
    /**
     * Set the key of the task.
     */
    UpdateTaskRetriesCmd taskKey(long taskKey);

    /**
     * Set the type of the task.
     */
    UpdateTaskRetriesCmd taskType(String taskType);

    /**
     * Sets the given key-value-pairs as the task header.
     */
    UpdateTaskRetriesCmd headers(Map<String, String> headers);

    /**
     * Sets the remaining retries of the task. If the retries are equal to zero
     * then the task will not be locked again unless the retries are increased.
     */
    UpdateTaskRetriesCmd retries(int remainingRetries);

}
