package org.camunda.tngp.client.task.cmd;

import java.util.Map;

import org.camunda.tngp.client.cmd.SetPayloadCmd;

public interface CreateTaskCmd extends SetPayloadCmd<Long, CreateTaskCmd>
{
    int DEFAULT_RETRIES = 3;

    /**
     * Set the type of the task.
     */
    CreateTaskCmd taskType(String taskType);

    /**
     * Add the given key-value-pair to the task header.
     */
    CreateTaskCmd addHeader(String key, Object value);

    /**
     * Set the given key-value-pairs as the task headers.
     */
    CreateTaskCmd setHeaders(Map<String, Object> headers);

    /**
     * Sets the initial retries of the task. Default is {@value #DEFAULT_RETRIES}.
     */
    CreateTaskCmd retries(int retries);

}