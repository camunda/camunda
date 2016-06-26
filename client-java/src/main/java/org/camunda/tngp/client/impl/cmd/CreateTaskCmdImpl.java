package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.CreateTaskRequestWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CreateTaskCmdImpl extends AbstractSetPayloadCmd<Long, CreateAsyncTaskCmd>
    implements CreateAsyncTaskCmd
{
    protected final CreateTaskRequestWriter requestWriter = new CreateTaskRequestWriter();

    public CreateTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor)
    {
        super(clientCmdExecutor, new TaskAckResponseHandler());
        requestWriter.resourceId(0); // TODO
    }

    @Override
    public CreateTaskCmdImpl taskQueueId(final int taskQueueId)
    {
        requestWriter.resourceId(taskQueueId);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd taskType(final String taskType)
    {
        requestWriter.getTaskType().wrap(taskType.getBytes(CHARSET));
        return this;
    }

    @Override
    public ClientRequestWriter getRequestWriter()
    {
        return requestWriter;
    }

    @Override
    protected MutableDirectBuffer getPayloadBuffer()
    {
        return requestWriter.getPayload();
    }

}
