package org.camunda.tngp.client.benchmark.msgpack;

import java.util.Map;

public interface TaskEvent
{
    void setEventType(TaskEventType event);
    void setLockTime(long lockTime);
    void setType(String type);
    void setHeaders(Map<String, String> headers);
    void setPayload(byte[] payload);
}
