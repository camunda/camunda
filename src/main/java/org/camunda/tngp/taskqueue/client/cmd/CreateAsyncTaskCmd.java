package org.camunda.tngp.taskqueue.client.cmd;

public interface CreateAsyncTaskCmd extends SetPayloadCmd<Long, CreateAsyncTaskCmd>, RecyclableCmd
{
    CreateAsyncTaskCmd taskType(String taskType);
}