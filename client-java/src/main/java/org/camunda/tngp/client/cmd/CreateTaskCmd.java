package org.camunda.tngp.client.cmd;

import java.util.Map;

public interface CreateTaskCmd extends SetPayloadCmd<Long, CreateTaskCmd>
{

    /**
     * Set the type of the task.
     */
    CreateTaskCmd taskType(String taskType);

    /**
     * Add the given key-value-pair to the task header.
     */
    CreateTaskCmd addHeader(String key, String value);

    /**
     * Set the given key-value-pairs as the task headers.
     */
    CreateTaskCmd setHeaders(Map<String, String> headers);

    /**
     * Sets the initial retries of the task.
     */
    CreateTaskCmd retries(int retries);

}