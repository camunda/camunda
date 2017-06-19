package org.camunda.tngp.client.task.impl.subscription;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.task.TaskHeaders;
import org.camunda.tngp.client.task.cmd.CompleteTaskCmd;
import org.camunda.tngp.client.task.impl.TaskEvent;

public class TaskImpl implements Task
{
    protected final TaskTopicClient tasksClient;

    protected final long key;
    protected final String type;
    protected final long lockExpirationTime;
    protected final String lockOwner;
    protected final int retries;
    protected final MsgPackField payload = new MsgPackField();
    protected final Map<String, Object> headers;

    protected int state;
    protected static final int STATE_LOCKED = 0;
    protected static final int STATE_COMPLETED = 1;
    protected static final int STATE_FAILED = 2;

    protected boolean payloadUpdated = false;

    public TaskImpl(
            TaskTopicClient tasksClient,
            TaskSubscriptionImpl subscription,
            long taskKey,
            TaskEvent taskEvent)
    {
        this.tasksClient = tasksClient;
        this.key = taskKey;

        this.type = taskEvent.getType();
        this.lockExpirationTime = taskEvent.getLockTime();
        this.lockOwner = subscription.getLockOwner();
        this.retries = taskEvent.getRetries();
        this.payload.setMsgPack(taskEvent.getPayload());
        this.headers = getTaskHeaders(taskEvent);

        this.state = STATE_LOCKED;
    }

    private Map<String, Object> getTaskHeaders(TaskEvent taskEvent)
    {
        final Map<String, Object> headers = new HashMap<>();

        final Map<String, Object> taskHeaders = taskEvent.getHeaders();
        if (taskHeaders != null && !taskHeaders.isEmpty())
        {
            headers.putAll(taskHeaders);

            if (taskHeaders.containsKey(TaskHeaders.CUSTOM))
            {
                final Object customHeadersObject = taskHeaders.get(TaskHeaders.CUSTOM);
                try
                {
                    @SuppressWarnings("unchecked")
                    final List<Map<String, Object>> customHeaders = (List<Map<String, Object>>) customHeadersObject;

                    customHeaders.forEach(customHeader ->
                    {
                        headers.put(String.valueOf(customHeader.get(TaskHeaders.CUSTOM_HEADER_KEY)), customHeader.get(TaskHeaders.CUSTOM_HEADER_VALUE));
                    });
                }
                catch (ClassCastException e)
                {
                    throw new RuntimeException("Failed to parse custom task headers. Expected List<Map<String,String>> but found: " + customHeadersObject.getClass().getSimpleName());
                }
            }
        }
        return headers;
    }

    @Override
    public void complete()
    {
        final CompleteTaskCmd completeTaskCmd = tasksClient.complete()
                                                           .taskKey(key)
                                                           .taskType(type)
                                                           .lockOwner(lockOwner)
                                                           .headers(headers);
        if (payloadUpdated)
        {
            completeTaskCmd.payload(payload.getAsJson());
        }
        completeTaskCmd.execute();

        state = STATE_COMPLETED;
    }

    public void fail(Exception e)
    {
        tasksClient.fail()
            .taskKey(key)
            .taskType(type)
            .lockOwner(lockOwner)
            .retries(retries - 1)
            .payload(payload.getAsJson())
            .headers(headers)
            .failure(e)
            .execute();

        state = STATE_FAILED;
    }

    public boolean isCompleted()
    {
        return state == STATE_COMPLETED;
    }

    @Override
    public long getKey()
    {
        return key;
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public Instant getLockExpirationTime()
    {
        return Instant.ofEpochMilli(lockExpirationTime);
    }

    @Override
    public String getPayload()
    {
        return payload.getAsJson();
    }

    @Override
    public void setPayload(String updatedPayload)
    {
        payloadUpdated = true;
        payload.setJson(updatedPayload);
    }

    @Override
    public Map<String, Object> getHeaders()
    {
        return new HashMap<>(headers);
    }

}
