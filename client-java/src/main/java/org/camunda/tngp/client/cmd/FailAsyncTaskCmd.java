package org.camunda.tngp.client.cmd;

import java.util.Map;

public interface FailAsyncTaskCmd extends SetPayloadCmd<Long, FailAsyncTaskCmd>
{
    /**
     * Set the key of the task.
     */
    FailAsyncTaskCmd taskKey(long taskKey);

    /**
     * Set the id of the owner who worked on the task.
     */
    FailAsyncTaskCmd lockOwner(int lockOwner);

    /**
     * Set the type of the task.
     */
    FailAsyncTaskCmd taskType(String taskType);

    /**
     * Add the given key-value-pair to the task header.
     */
    FailAsyncTaskCmd addHeader(String key, String value);

    /**
     * Sets the given key-value-pairs as the task header.
     */
    FailAsyncTaskCmd headers(Map<String, String> headers);

    /**
     * Sets the error which causes the failure.
     */
    FailAsyncTaskCmd failure(Exception e);
}
