package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNullOrEmpty;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.cmd.FailTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FailTaskCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements FailTaskCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected long taskKey = -1L;
    protected int lockOwner = -1;
    protected int retries = -1;
    protected String taskType;
    protected byte[] payload;
    protected Map<String, Object> headers = new HashMap<>();
    protected Exception failure;

    public FailTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor, final ObjectMapper objectMapper, final int topicId)
    {
        super(clientCmdExecutor, objectMapper, TaskEvent.class, topicId, TASK_EVENT);
    }

    @Override
    public FailTaskCmd taskKey(long taskKey)
    {
        this.taskKey = taskKey;
        return this;
    }

    @Override
    public FailTaskCmd lockOwner(int lockOwner)
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
        ensureGreaterThanOrEqual("task key", taskKey, 0);
        ensureGreaterThanOrEqual("topic id", topicId, 0);
        ensureGreaterThanOrEqual("lock owner", lockOwner, 0);
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
        lockOwner = -1;
        retries = -1;
        taskType = null;
        payload = null;
        headers.clear();
        failure = null;

        taskEvent.reset();
    }

    @Override
    protected Long getResponseValue(int channelId, long key, TaskEvent event)
    {
        long result = -1;

        if (event.getEventType() == TaskEventType.FAILED)
        {
            result = key;
        }

        return result;
    }

}
