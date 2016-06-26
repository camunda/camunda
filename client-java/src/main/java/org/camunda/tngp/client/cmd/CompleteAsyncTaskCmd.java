package org.camunda.tngp.client.cmd;

public interface CompleteAsyncTaskCmd extends SetPayloadCmd<Long, CompleteAsyncTaskCmd>
{
    CompleteAsyncTaskCmd taskQueueId(int taskQueueId);

    CompleteAsyncTaskCmd taskId(long taskId);
}
