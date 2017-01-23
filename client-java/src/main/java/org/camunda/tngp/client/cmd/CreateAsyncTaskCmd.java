package org.camunda.tngp.client.cmd;

import java.util.Map;

public interface CreateAsyncTaskCmd extends SetPayloadCmd<Long, CreateAsyncTaskCmd>
{
    CreateAsyncTaskCmd taskQueueId(long taskQueueId);

    CreateAsyncTaskCmd taskType(String taskType);

    CreateAsyncTaskCmd addHeader(String key, String value);

    CreateAsyncTaskCmd addHeaders(Map<String, String> headers);
}