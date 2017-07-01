package io.zeebe.client.task.cmd;

import java.util.Map;

import io.zeebe.client.cmd.SetPayloadCmd;

public interface CompleteTaskCmd extends SetPayloadCmd<Long, CompleteTaskCmd>
{
    /**
     * Set the key of the task.
     */
    CompleteTaskCmd taskKey(long taskKey);

    /**
     * Set the owner who complete the task.
     */
    CompleteTaskCmd lockOwner(String lockOwner);

    /**
     * Set the type of the task.
     */
    CompleteTaskCmd taskType(String taskType);

    /**
     * Add the given key-value-pair to the task header.
     */
    CompleteTaskCmd addHeader(String key, Object value);

    /**
     * Sets the given key-value-pairs as the task header.
     */
    CompleteTaskCmd headers(Map<String, Object> headers);
}
