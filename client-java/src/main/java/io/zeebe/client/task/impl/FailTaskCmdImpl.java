/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    protected final MsgPackConverter msgPackConverter;

    protected long taskKey = -1L;
    protected String lockOwner;
    protected int retries = -1;
    protected String taskType;
    protected byte[] payload;
    protected Map<String, Object> headers = new HashMap<>();
    protected Exception failure;

    public FailTaskCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, MsgPackConverter msgPackConverter, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TaskEvent.class, TASK_EVENT);
        this.msgPackConverter = msgPackConverter;
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
