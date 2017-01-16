package org.camunda.tngp.client.cmd;

public interface CreateAsyncTaskCmd extends SetPayloadCmd<Long, CreateAsyncTaskCmd>
{
    CreateAsyncTaskCmd taskQueueId(long taskQueueId);

    CreateAsyncTaskCmd taskType(String taskType);
}