package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.PayloadRequestWriter;

public class CompleteTaskCmdImpl extends AbstractSetPayloadCmd<Long, CompleteAsyncTaskCmd> implements CompleteAsyncTaskCmd
{

    public CompleteTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor)
    {
        super(clientCmdExecutor, new TaskAckResponseHandler());
    }

    @Override
    public CompleteAsyncTaskCmd taskId(long taskId)
    {
        return this;
    }

    @Override
    public CompleteAsyncTaskCmd taskQueueId(int taskQueueId)
    {
        return this;
    }

    @Override
    public PayloadRequestWriter getRequestWriter()
    {
        return null;
    }

    @Override
    public CompleteAsyncTaskCmd payload(String payload)
    {
        return null;
    }
}
