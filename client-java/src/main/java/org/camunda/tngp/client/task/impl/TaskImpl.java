package org.camunda.tngp.client.task.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.task.Task;

public class TaskImpl implements Task
{
    protected final AsyncTasksClient tasksClient;

    protected final int topicId;
    protected final long key;
    protected final Long workflowInstanceId;
    protected final String type;
    protected final long lockExpirationTime;
    protected final int lockOwner;
    protected final PayloadField payload = new PayloadField();
    protected Map<String, String> headers;

    protected int state;
    protected static final int STATE_LOCKED = 0;
    protected static final int STATE_COMPLETED = 1;


    public TaskImpl(
            AsyncTasksClient tasksClient,
            TaskSubscriptionImpl subscription,
            long taskKey,
            TaskEvent taskEvent)
    {
        this.tasksClient = tasksClient;
        this.key = taskKey;

        this.topicId = subscription.getTopicId();
        this.type = taskEvent.getType();
        this.lockExpirationTime = taskEvent.getLockTime();
        this.lockOwner = subscription.getLockOwner();
        this.payload.setRawPayload(taskEvent.getPayload());
        this.headers = taskEvent.getHeaders();

        this.workflowInstanceId = null;

        this.state = STATE_LOCKED;
    }

    @Override
    public void complete()
    {
        tasksClient.complete()
            .topicId(topicId)
            .taskKey(key)
            .taskType(type)
            .lockOwner(lockOwner)
            .payload(payload.getJsonPayload())
            .headers(headers)
            .execute();

        state = STATE_COMPLETED;
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
    public Long getWorkflowInstanceId()
    {
        return workflowInstanceId;
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
        return payload.getJsonPayload();
    }

    @Override
    public void setPayload(String updatedPayload)
    {
        payload.setJsonPayload(updatedPayload);
    }

    @Override
    public Map<String, String> getHeaders()
    {
        return new HashMap<>(headers);
    }

    @Override
    public void setHeaders(Map<String, String> newHeaders)
    {
        this.headers = newHeaders;
    }

}
