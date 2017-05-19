package org.camunda.tngp.client.task.cmd;

import java.util.Map;

import org.camunda.tngp.client.cmd.SetPayloadCmd;

public interface CompleteTaskCmd extends SetPayloadCmd<Long, CompleteTaskCmd>
{
    /**
     * Set the key of the task.
     */
    CompleteTaskCmd taskKey(long taskKey);

    /**
     * Set the id of the owner who complete the task.
     */
    CompleteTaskCmd lockOwner(int lockOwner);

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
