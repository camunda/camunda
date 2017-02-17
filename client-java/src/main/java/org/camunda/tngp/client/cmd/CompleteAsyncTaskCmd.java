package org.camunda.tngp.client.cmd;

import java.util.Map;

public interface CompleteAsyncTaskCmd extends SetPayloadCmd<Long, CompleteAsyncTaskCmd>
{
    /**
     * Set the key of the task.
     */
    CompleteAsyncTaskCmd taskKey(long taskKey);

    /**
     * Set the id of the topic the task is created for.
     */
    CompleteAsyncTaskCmd topicId(long topicId);

    /**
     * Set the id of the owner who complete the task.
     */
    CompleteAsyncTaskCmd lockOwner(int lockOwner);

    /**
     * Set the type of the task.
     */
    CompleteAsyncTaskCmd taskType(String taskType);

    /**
     * Add the given key-value-pair to the task header.
     */
    CompleteAsyncTaskCmd addHeader(String key, String value);

    /**
     * Sets the given key-value-pairs as the task header.
     */
    CompleteAsyncTaskCmd headers(Map<String, String> headers);
}
