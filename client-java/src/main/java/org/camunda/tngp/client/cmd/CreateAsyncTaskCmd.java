package org.camunda.tngp.client.cmd;

import java.util.Map;

public interface CreateAsyncTaskCmd extends SetPayloadCmd<Long, CreateAsyncTaskCmd>
{
    /**
     * Set the id of the topic the task is create for.
     */
    CreateAsyncTaskCmd topicId(long topicId);

    /**
     * Set the type of the task.
     */
    CreateAsyncTaskCmd taskType(String taskType);

    /**
     * Add the given key-value-pair to the task header.
     */
    CreateAsyncTaskCmd addHeader(String key, String value);

    /**
     * Set the given key-value-pairs as the task headers.
     */
    CreateAsyncTaskCmd setHeaders(Map<String, String> headers);
}