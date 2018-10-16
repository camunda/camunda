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
package io.zeebe.gateway.impl.job;

import io.zeebe.gateway.api.commands.CreateJobCommandStep1;
import io.zeebe.gateway.api.commands.CreateJobCommandStep1.CreateJobCommandStep2;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.impl.CommandImpl;
import io.zeebe.gateway.impl.RequestManager;
import io.zeebe.gateway.impl.command.JobCommandImpl;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.gateway.impl.record.RecordImpl;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.EnsureUtil;
import java.io.InputStream;
import java.util.Map;

public class CreateJobCommandImpl extends CommandImpl<JobEvent>
    implements CreateJobCommandStep1, CreateJobCommandStep2 {
  private final JobCommandImpl command;

  public CreateJobCommandImpl(
      final RequestManager commandManager, final ZeebeObjectMapperImpl objectMapper) {
    super(commandManager);

    command = new JobCommandImpl(objectMapper, JobIntent.CREATE);

    command.setRetries(CreateJobCommandStep1.DEFAULT_RETRIES);
  }

  @Override
  public CreateJobCommandStep2 addCustomHeader(final String key, final Object value) {
    command.getCustomHeaders().put(key, value);
    return this;
  }

  @Override
  public CreateJobCommandStep2 addCustomHeaders(final Map<String, Object> headers) {
    command.getCustomHeaders().putAll(headers);
    return this;
  }

  @Override
  public CreateJobCommandStep2 retries(final int retries) {
    command.setRetries(retries);
    return this;
  }

  @Override
  public CreateJobCommandStep2 payload(final InputStream payload) {
    command.setPayload(payload);
    return this;
  }

  @Override
  public CreateJobCommandStep2 payload(final String payload) {
    command.setPayload(payload);
    return this;
  }

  @Override
  public CreateJobCommandStep2 payload(final Map<String, Object> payload) {
    command.setPayload(payload);
    return this;
  }

  @Override
  public CreateJobCommandStep2 payload(final Object payload) {
    command.setPayload(payload);
    return this;
  }

  @Override
  public CreateJobCommandStep2 jobType(final String type) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);

    command.setType(type);
    return this;
  }

  @Override
  public RecordImpl getCommand() {
    return command;
  }
}
