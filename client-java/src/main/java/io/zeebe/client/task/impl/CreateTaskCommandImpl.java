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

import java.io.InputStream;
import java.util.Map;

import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.task.cmd.CreateTaskCommand;
import io.zeebe.util.EnsureUtil;

public class CreateTaskCommandImpl extends CommandImpl<TaskEvent> implements CreateTaskCommand
{
    protected final TaskEventImpl taskEvent;

    public CreateTaskCommandImpl(RequestManager client, MsgPackConverter converter, String topic, String type)
    {
        super(client);
        EnsureUtil.ensureNotNull("topic", topic);
        EnsureUtil.ensureNotEmpty("topic", topic);
        EnsureUtil.ensureNotNull("type", type);

        final String state = TaskEventType.CREATE.name();

        this.taskEvent = new TaskEventImpl(state, converter);
        this.taskEvent.setType(type);
        this.taskEvent.setTopicName(topic);
        this.taskEvent.setRetries(CreateTaskCommand.DEFAULT_RETRIES);
    }

    @Override
    public CreateTaskCommand retries(int retries)
    {
        this.taskEvent.setRetries(retries);
        return this;
    }

    @Override
    public CreateTaskCommand payload(String payload)
    {
        this.taskEvent.setPayload(payload);
        return this;
    }

    @Override
    public CreateTaskCommand payload(InputStream payload)
    {
        this.taskEvent.setPayload(payload);
        return this;
    }

    @Override
    public CreateTaskCommand addCustomHeader(String key, Object value)
    {
        this.taskEvent.getCustomHeaders().put(key, value);
        return this;
    }

    @Override
    public CreateTaskCommand setCustomHeaders(Map<String, Object> headers)
    {
        this.taskEvent.setCustomHeaders(headers);
        return this;
    }

    @Override
    public TaskEventImpl getEvent()
    {
        return taskEvent;
    }

    @Override
    public String getExpectedStatus()
    {
        return TaskEventType.CREATED.name();
    }
}
