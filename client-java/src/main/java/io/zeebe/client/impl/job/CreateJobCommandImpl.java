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
package io.zeebe.client.impl.job;

import java.io.InputStream;
import java.util.Map;

import io.zeebe.client.api.commands.CreateJobCommandStep1;
import io.zeebe.client.api.commands.CreateJobCommandStep1.CreateJobCommandStep2;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.impl.*;
import io.zeebe.client.impl.command.JobCommandImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.EnsureUtil;

public class CreateJobCommandImpl extends CommandImpl<JobEvent> implements CreateJobCommandStep1, CreateJobCommandStep2
{
    private final JobCommandImpl command;

    public CreateJobCommandImpl(RequestManager commandManager, ZeebeObjectMapperImpl objectMapper, MsgPackConverter converter, String topic)
    {
        super(commandManager);

        command = new JobCommandImpl(objectMapper, converter, JobIntent.CREATE);

        command.setTopicName(topic);
        command.setRetries(CreateJobCommandStep1.DEFAULT_RETRIES);
    }

    @Override
    public CreateJobCommandStep2 addCustomHeader(String key, Object value)
    {
        command.getCustomHeaders().put(key, value);
        return this;
    }

    @Override
    public CreateJobCommandStep2 addCustomHeaders(Map<String, Object> headers)
    {
        command.getCustomHeaders().putAll(headers);
        return this;
    }

    @Override
    public CreateJobCommandStep2 retries(int retries)
    {
        command.setRetries(retries);
        return this;
    }

    @Override
    public CreateJobCommandStep2 payload(InputStream payload)
    {
        command.setPayload(payload);
        return this;
    }

    @Override
    public CreateJobCommandStep2 payload(String payload)
    {
        command.setPayload(payload);
        return this;
    }

    @Override
    public CreateJobCommandStep2 jobType(String type)
    {
        EnsureUtil.ensureNotNullOrEmpty("type", type);

        command.setType(type);
        return this;
    }

    @Override
    public RecordImpl getCommand()
    {
        return command;
    }

}
