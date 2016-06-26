package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.CompleteTaskRequestWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CompleteTaskCmdImpl extends AbstractSetPayloadCmd<Long, CompleteAsyncTaskCmd>
    implements CompleteAsyncTaskCmd
{
    protected final CompleteTaskRequestWriter requestWriter = new CompleteTaskRequestWriter();

    public CompleteTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor)
    {
        super(clientCmdExecutor, new TaskAckResponseHandler());
    }

    @Override
    public CompleteAsyncTaskCmd taskId(long taskId)
    {
        requestWriter.taskId(taskId);
        return this;
    }

    @Override
    public CompleteAsyncTaskCmd taskQueueId(int taskQueueId)
    {
        requestWriter.resourceId(taskQueueId);
        return this;
    }

    @Override
    protected MutableDirectBuffer getPayloadBuffer()
    {
        return requestWriter.getPayload();
    }

    @Override
    public ClientRequestWriter getRequestWriter()
    {
        return requestWriter;
    }
}
