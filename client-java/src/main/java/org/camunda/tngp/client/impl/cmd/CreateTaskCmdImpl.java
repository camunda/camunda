package org.camunda.tngp.client.impl.cmd;

import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateTaskCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements CreateAsyncTaskCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected long topicId = -1L;
    protected String taskType;
    protected byte[] payload;
    protected Map<String, String> headers = new HashMap<>();

    public CreateTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor, final ObjectMapper objectMapper)
    {
        super(clientCmdExecutor, objectMapper, TaskEvent.class, TASK_EVENT);
    }

    @Override
    public CreateTaskCmdImpl topicId(final long topicId)
    {
        this.topicId = topicId;
        return this;
    }

    @Override
    public CreateAsyncTaskCmd taskType(final String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public CreateAsyncTaskCmd payload(String payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd payload(InputStream payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd addHeader(String key, String value)
    {
        headers.put(key, value);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd addHeaders(Map<String, String> headersToAdd)
    {
        headers.putAll(headersToAdd);
        return this;
    }

    @Override
    protected long getTopicId()
    {
        return topicId;
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("topic id", topicId, 0);
        EnsureUtil.ensureNotNullOrEmpty("task type", taskType);
    }

    @Override
    protected Object writeCommand()
    {
        taskEvent.setEvent(TaskEventType.CREATE);
        taskEvent.setType(taskType);
        taskEvent.setHeaders(headers);
        taskEvent.setPayload(payload);

        return taskEvent;
    }

    @Override
    protected void reset()
    {
        topicId = -1L;
        taskType = null;
        payload = null;
        headers.clear();

        taskEvent.reset();
    }

    @Override
    protected Long getResponseValue(long key, TaskEvent event)
    {
        return key;
    }

}
