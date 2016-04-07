package org.camunda.tngp.taskqueue.client.cmd;

public interface CompleteTaskCmd extends SetPayloadCmd<Long, CompleteTaskCmd>, RecyclableCmd
{
    CompleteTaskCmd taskId(long taskId);
}
