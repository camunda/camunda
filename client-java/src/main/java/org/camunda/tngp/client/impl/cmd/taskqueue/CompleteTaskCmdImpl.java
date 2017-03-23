package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNullOrEmpty;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.cmd.CompleteTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CompleteTaskCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements CompleteTaskCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected long taskKey = -1L;
    protected int lockOwner = -1;
    protected String taskType;
    protected byte[] payload;
    protected Map<String, String> headers = new HashMap<>();

    public CompleteTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor, final ObjectMapper objectMapper, final int topicId)
    {
        super(clientCmdExecutor, objectMapper, TaskEvent.class, topicId, TASK_EVENT);
    }

    @Override
    public CompleteTaskCmd taskKey(long taskKey)
    {
        this.taskKey = taskKey;
        return this;
    }

    @Override
    public CompleteTaskCmd lockOwner(int lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    @Override
    public CompleteTaskCmd taskType(final String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public CompleteTaskCmd payload(String payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CompleteTaskCmd payload(InputStream payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CompleteTaskCmd addHeader(String key, String value)
    {
        headers.put(key, value);
        return this;
    }

    @Override
    public CompleteTaskCmd headers(Map<String, String> newHeaders)
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
        ensureGreaterThanOrEqual("task key", taskKey, 0);
        ensureGreaterThanOrEqual("topic id", topicId, 0);
        ensureGreaterThanOrEqual("lock owner", lockOwner, 0);
        ensureNotNullOrEmpty("task type", taskType);
    }

    @Override
    protected Object writeCommand()
    {
        taskEvent.setEventType(TaskEventType.COMPLETE);
        taskEvent.setType(taskType);
        taskEvent.setLockOwner(lockOwner);
        taskEvent.setHeaders(headers);
        taskEvent.setPayload(payload);

        return taskEvent;
    }

    @Override
    protected void reset()
    {
        taskKey = -1L;
        lockOwner = -1;
        taskType = null;
        payload = null;
        headers.clear();

        taskEvent.reset();
    }

    @Override
    protected Long getResponseValue(long key, TaskEvent event)
    {
        long result = -1;

        if (event.getEventType() == TaskEventType.COMPLETED)
        {
            result = key;
        }

        return result;
    }
}
