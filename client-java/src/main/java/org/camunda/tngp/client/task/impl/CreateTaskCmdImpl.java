package org.camunda.tngp.client.task.impl;

import static org.camunda.tngp.protocol.clientapi.EventType.*;
import static org.camunda.tngp.util.EnsureUtil.*;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCommandManager;
import org.camunda.tngp.client.impl.Topic;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.client.task.cmd.CreateTaskCmd;

public class CreateTaskCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements CreateTaskCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected String taskType;
    protected int retries = DEFAULT_RETRIES;
    protected byte[] payload;
    protected Map<String, Object> headers = new HashMap<>();

    public CreateTaskCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TaskEvent.class, TASK_EVENT);
    }

    @Override
    public CreateTaskCmd taskType(final String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public CreateTaskCmd retries(int retries)
    {
        this.retries = retries;
        return this;
    }

    @Override
    public CreateTaskCmd payload(String payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CreateTaskCmd payload(InputStream payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CreateTaskCmd addHeader(String key, Object value)
    {
        headers.put(key, value);
        return this;
    }

    @Override
    public CreateTaskCmd setHeaders(Map<String, Object> headers)
    {
        this.headers.clear();
        this.headers.putAll(headers);
        return this;
    }

    @Override
    protected long getKey()
    {
        return -1L;
    }

    @Override
    public void validate()
    {
        super.validate();
        ensureNotNullOrEmpty("task type", taskType);
        ensureGreaterThanOrEqual("retries", retries, 0);
    }

    @Override
    protected Object writeCommand()
    {
        taskEvent.setEventType(TaskEventType.CREATE);
        taskEvent.setType(taskType);
        taskEvent.setRetries(retries);
        taskEvent.setHeaders(headers);
        taskEvent.setPayload(payload);

        return taskEvent;
    }

    @Override
    protected void reset()
    {
        retries = DEFAULT_RETRIES;

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
