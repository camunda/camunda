package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNullOrEmpty;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.cmd.UpdateTaskRetriesCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class UpdateTaskRetriesCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements UpdateTaskRetriesCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected long taskKey = -1L;
    protected int retries = -1;
    protected String taskType;
    protected byte[] payload;
    protected Map<String, String> headers = new HashMap<>();

    public UpdateTaskRetriesCmdImpl(final ClientCmdExecutor clientCmdExecutor, final ObjectMapper objectMapper, final int topicId)
    {
        super(clientCmdExecutor, objectMapper, TaskEvent.class, topicId, TASK_EVENT);
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
    public UpdateTaskRetriesCmd headers(Map<String, String> newHeaders)
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
        ensureGreaterThanOrEqual("retries", retries, 0);
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
    protected Long getResponseValue(int channelId, long key, TaskEvent event)
    {
        long result = -1;

        if (event.getEventType() == TaskEventType.RETRIES_UPDATED)
        {
            result = key;
        }

        return result;
    }

}
