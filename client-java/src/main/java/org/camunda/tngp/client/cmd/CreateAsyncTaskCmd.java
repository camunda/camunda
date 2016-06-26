package org.camunda.tngp.client.cmd;

public interface CreateAsyncTaskCmd extends SetPayloadCmd<Long, CreateAsyncTaskCmd>
{
    CreateAsyncTaskCmd taskQueueId(int taskQueueId);

    CreateAsyncTaskCmd taskType(String taskType);
}