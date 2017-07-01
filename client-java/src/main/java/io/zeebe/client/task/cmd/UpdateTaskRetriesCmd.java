package io.zeebe.client.task.cmd;

import java.util.Map;

import io.zeebe.client.cmd.SetPayloadCmd;

/**
 * Update the remaining task retries. If the retries are equal to zero then the
 * task will not be locked again unless the retries are increased.
 */
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
    UpdateTaskRetriesCmd headers(Map<String, Object> headers);

    /**
     * Sets the remaining retries of the task. The retries must be greater than
     * zero.
     */
    UpdateTaskRetriesCmd retries(int remainingRetries);

}
