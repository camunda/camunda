package io.zeebe.client.event.impl;

import java.time.Instant;
import java.util.Map;

import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.task.impl.subscription.MsgPackField;
import io.zeebe.protocol.Protocol;

public class TaskEventImpl implements TaskEvent
{
    protected String eventType;
    protected Map<String, Object> headers;
    protected Long lockTime;
    protected String lockOwner;
    protected Integer retries;
    protected String type;
    protected final MsgPackField payload = new MsgPackField();

    @Override
    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    @Override
    public Instant getLockExpirationTime()
    {
        if (lockTime != null && !lockTime.equals(Protocol.INSTANT_NULL_VALUE))
        {
            return Instant.ofEpochMilli(lockTime);
        }
        else
        {
            return null;
        }
    }

    public void setLockTime(Long lockTime)
    {
        this.lockTime = lockTime;
    }

    @Override
    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    @Override
    public Map<String, Object> getHeaders()
    {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers)
    {
        this.headers = headers;
    }

    @Override
    public String getLockOwner()
    {
        return lockOwner;
    }

    public void setLockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
    }

    @Override
    public String getPayload()
    {
        return payload.getAsJson();
    }

    public void setPayload(byte[] msgPack)
    {
        this.payload.setMsgPack(msgPack);
    }

    public Integer getRetries()
    {
        return retries;
    }

    public void setRetries(Integer retries)
    {
        this.retries = retries;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("TaskEventImpl [eventType=");
        builder.append(eventType);
        builder.append(", type=");
        builder.append(type);
        builder.append(", retries=");
        builder.append(retries);
        builder.append(", lockOwner=");
        builder.append(lockOwner);
        builder.append(", lockTime=");
        builder.append(lockTime);
        builder.append(", headers=");
        builder.append(headers);
        builder.append(", payload=");
        builder.append(payload.getAsJson());
        builder.append("]");
        return builder.toString();
    }
}
