package org.camunda.tngp.client.task.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.task.Task;

public class TaskImpl implements Task
{
    protected final TaskTopicClient tasksClient;

    protected final long key;
    protected final String type;
    protected final long lockExpirationTime;
    protected final int lockOwner;
    protected final int retries;
    protected final MsgPackField payload = new MsgPackField();
    protected Map<String, Object> headers;

    protected int state;
    protected static final int STATE_LOCKED = 0;
    protected static final int STATE_COMPLETED = 1;
    protected static final int STATE_FAILED = 2;

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
        this.headers = taskEvent.getHeaders();
        this.payload.setMsgPack(taskEvent.getPayload());

        this.state = STATE_LOCKED;
    }

    @Override
    public void complete()
    {
        tasksClient.complete()
            .taskKey(key)
            .taskType(type)
            .lockOwner(lockOwner)
            .headers(headers)
            .payload(payload.getAsJson())
            .execute();

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
        payload.setJson(updatedPayload);
    }

    @Override
    public Map<String, Object> getHeaders()
    {
        return new HashMap<>(headers);
    }

    @Override
    public void setHeaders(Map<String, Object> newHeaders)
    {
        this.headers = newHeaders;
    }

}
