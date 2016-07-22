package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.CompleteTaskRequestWriter;

public class CompleteTaskCmdImpl extends AbstractSetPayloadCmd<Long, CompleteAsyncTaskCmd>
    implements CompleteAsyncTaskCmd
{
    protected CompleteTaskRequestWriter requestWriter = new CompleteTaskRequestWriter();

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

    public void setRequestWriter(CompleteTaskRequestWriter requestWriter)
    {
        this.requestWriter = requestWriter;
    }

    @Override
    public PayloadRequestWriter getRequestWriter()
    {
        return requestWriter;
    }
}
