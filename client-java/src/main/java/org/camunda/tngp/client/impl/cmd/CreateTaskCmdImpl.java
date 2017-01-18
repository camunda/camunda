package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateTaskCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements CreateAsyncTaskCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();

    protected long taskQueueId = -1L;

    public CreateTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor, final ObjectMapper objectMapper)
    {
        super(clientCmdExecutor, objectMapper, TaskEvent.class);
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
        // TODO #96 - pass payload as String or byte array?
        taskEvent.setPayload(payload.getBytes());
        return this;
    }

    @Override
    protected long getTopicId()
    {
        return taskQueueId;
    }

    @Override
    public void validate()
    {
        // TODO validate request
    }

    @Override
    protected Object writeCommand()
    {
        taskEvent.setEvent(TaskEventType.CREATE);

        return taskEvent;
    }

    @Override
    protected void reset()
    {
        taskQueueId = -1L;

        taskEvent.reset();
    }

    @Override
    protected Long getResponseValue(long key, TaskEvent event)
    {
        return key;
    }

}
