package io.zeebe.client.task.impl;

import static io.zeebe.protocol.clientapi.EventType.*;
import static io.zeebe.util.EnsureUtil.*;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.task.cmd.UpdateTaskRetriesCmd;

public class UpdateTaskRetriesCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements UpdateTaskRetriesCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected long taskKey = -1L;
    protected int retries = -1;
    protected String taskType;
    protected byte[] payload;
    protected Map<String, Object> headers = new HashMap<>();

    public UpdateTaskRetriesCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TaskEvent.class, TASK_EVENT);
    }

    @Override
    public UpdateTaskRetriesCmd taskKey(long taskKey)
    {
        this.taskKey = taskKey;
        return this;
    }

    @Override
    public UpdateTaskRetriesCmd retries(int retries)
    {
        this.retries = retries;
        return this;
    }

    @Override
    public UpdateTaskRetriesCmd taskType(final String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public UpdateTaskRetriesCmd payload(String payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public UpdateTaskRetriesCmd payload(InputStream payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public UpdateTaskRetriesCmd headers(Map<String, Object> newHeaders)
    {
        headers.clear();
        headers.putAll(newHeaders);
        return this;
    }

    @Override
    protected long getKey()
    {
        return taskKey;
    }

    @Override
    public void validate()
    {
        super.validate();
        ensureGreaterThanOrEqual("task key", taskKey, 0);
        ensureGreaterThan("retries", retries, 0);
        ensureNotNullOrEmpty("task type", taskType);
    }

    @Override
    protected Object writeCommand()
    {
        taskEvent.setEventType(TaskEventType.UPDATE_RETRIES);
        taskEvent.setType(taskType);
        taskEvent.setRetries(retries);
        taskEvent.setHeaders(headers);
        taskEvent.setPayload(payload);

        return taskEvent;
    }

    @Override
    protected void reset()
    {
        taskKey = -1L;
        retries = -1;
        taskType = null;
        payload = null;
        headers.clear();

        taskEvent.reset();
    }

    @Override
    protected Long getResponseValue(long key, TaskEvent event)
    {
        long result = -1;

        if (event.getEventType() == TaskEventType.RETRIES_UPDATED)
        {
            result = key;
        }

        return result;
    }

}
