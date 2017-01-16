package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.util.buffer.RequestWriter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateTaskCmdImpl extends AbstractSetPayloadCmd<Long, CreateAsyncTaskCmd>
    implements CreateAsyncTaskCmd, CommandRequestWriter
{
    protected final ExecuteCommandRequestWriter requestWriter;

    protected final TaskEvent taskEvent = new TaskEvent();

    protected long taskQueueId = -1L;

    public CreateTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor, final ObjectMapper objectMapper)
    {
        super(clientCmdExecutor, new TaskAckResponseHandler());

        requestWriter = new ExecuteCommandRequestWriter(this, objectMapper);
    }

    @Override
    public CreateTaskCmdImpl taskQueueId(final long taskQueueId)
    {
        this.taskQueueId = taskQueueId;
        return this;
    }

    @Override
    public CreateAsyncTaskCmd taskType(final String taskType)
    {
        taskEvent.setType(taskType);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd payload(String payload)
    {
        taskEvent.setPayload(payload);
        return this;
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return requestWriter;
    }

    @Override
    public long getTopicId()
    {
        return taskQueueId;
    }

    @Override
    public void validate()
    {
        // TODO validate request
    }

    @Override
    public Object writeCommand()
    {
        taskEvent.setEvent(TaskEventType.CREATE);

        return taskEvent;
    }

    @Override
    public void reset()
    {
        taskEvent.reset();

        taskQueueId = -1L;
    }

}
