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
package io.zeebe.client.impl.command;

import com.fasterxml.jackson.annotation.*;
import io.zeebe.client.api.commands.JobCommand;
import io.zeebe.client.api.commands.JobCommandName;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.record.JobRecordImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.JobIntent;

public class JobCommandImpl extends JobRecordImpl implements JobCommand {

  @JsonCreator
  public JobCommandImpl(@JacksonInject ZeebeObjectMapperImpl objectMapper) {
    super(objectMapper, RecordType.COMMAND);
  }

  public JobCommandImpl(ZeebeObjectMapperImpl objectMapper, JobIntent intent) {
    super(objectMapper, RecordType.COMMAND);
    setIntent(intent);
  }

  public JobCommandImpl(JobRecordImpl base, JobIntent intent) {
    super(base, intent);
  }

  @JsonIgnore
  @Override
  public JobCommandName getName() {
    return JobCommandName.valueOf(getMetadata().getIntent());
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("JobCommand [command=");
    builder.append(getName());
    builder.append(", type=");
    builder.append(getType());
    builder.append(", retries=");
    builder.append(getRetries());
    builder.append(", worker=");
    builder.append(getWorker());
    builder.append(", deadline=");
    builder.append(getDeadline());
    builder.append(", headers=");
    builder.append(getHeaders());
    builder.append(", customHeaders=");
    builder.append(getCustomHeaders());
    builder.append(", payload=");
    builder.append(getPayload());
    builder.append("]");
    return builder.toString();
  }
}
