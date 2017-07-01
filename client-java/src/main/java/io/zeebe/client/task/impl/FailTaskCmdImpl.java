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
import io.zeebe.client.task.cmd.FailTaskCmd;

public class FailTaskCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements FailTaskCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected long taskKey = -1L;
    protected String lockOwner;
    protected int retries = -1;
    protected String taskType;
    protected byte[] payload;
    protected Map<String, Object> headers = new HashMap<>();
    protected Exception failure;

    public FailTaskCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TaskEvent.class, TASK_EVENT);
    }

    @Override
    public FailTaskCmd taskKey(long taskKey)
    {
        this.taskKey = taskKey;
        return this;
    }

    @Override
    public FailTaskCmd lockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    @Override
    public FailTaskCmd retries(int retries)
    {
        this.retries = retries;
        return this;
    }

    @Override
    public FailTaskCmd taskType(final String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public FailTaskCmd payload(String payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public FailTaskCmd payload(InputStream payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public FailTaskCmd addHeader(String key, Object value)
    {
        headers.put(key, value);
        return this;
    }

    @Override
    public FailTaskCmd headers(Map<String, Object> newHeaders)
    {
        headers.clear();
        headers.putAll(newHeaders);
        return this;
    }

    @Override
    public FailTaskCmd failure(Exception e)
    {
        this.failure = e;
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
        ensureNotNullOrEmpty("lock owner", lockOwner);
        ensureGreaterThanOrEqual("retries", retries, 0);
        ensureNotNullOrEmpty("task type", taskType);
    }

    @Override
    protected Object writeCommand()
    {
        taskEvent.setEventType(TaskEventType.FAIL);
        taskEvent.setType(taskType);
        taskEvent.setLockOwner(lockOwner);
        taskEvent.setRetries(retries);
        taskEvent.setHeaders(headers);
        taskEvent.setPayload(payload);

        return taskEvent;
    }

    @Override
    protected void reset()
    {
        taskKey = -1L;
        lockOwner = null;
        retries = -1;
        taskType = null;
        payload = null;
        headers.clear();
        failure = null;

        taskEvent.reset();
    }

    @Override
    protected Long getResponseValue(long key, TaskEvent event)
    {
        long result = -1;

        if (event.getEventType() == TaskEventType.FAILED)
        {
            result = key;
        }

        return result;
    }

}
