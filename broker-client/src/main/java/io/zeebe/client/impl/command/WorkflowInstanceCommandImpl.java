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
import io.zeebe.client.api.commands.WorkflowInstanceCommand;
import io.zeebe.client.api.commands.WorkflowInstanceCommandName;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.record.WorkflowInstanceRecordImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class WorkflowInstanceCommandImpl extends WorkflowInstanceRecordImpl
    implements WorkflowInstanceCommand {
  @JsonCreator
  public WorkflowInstanceCommandImpl(@JacksonInject ZeebeObjectMapperImpl objectMapper) {
    super(objectMapper, RecordType.COMMAND);
  }

  public WorkflowInstanceCommandImpl(
      ZeebeObjectMapperImpl objectMapper, WorkflowInstanceIntent intent) {
    super(objectMapper, RecordType.COMMAND);
    setIntent(intent);
  }

  public WorkflowInstanceCommandImpl(
      WorkflowInstanceRecordImpl base, WorkflowInstanceIntent intent) {
    super(base, intent);
  }

  @JsonIgnore
  @Override
  public WorkflowInstanceCommandName getName() {
    return WorkflowInstanceCommandName.valueOf(getMetadata().getIntent());
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("WorkflowInstanceCommand [command=");
    builder.append(getName());
    builder.append(", workflowInstanceKey=");
    builder.append(getWorkflowInstanceKey());
    builder.append(", workflowKey=");
    builder.append(getWorkflowKey());
    builder.append(", bpmnProcessId=");
    builder.append(getBpmnProcessId());
    builder.append(", version=");
    builder.append(getVersion());
    builder.append(", activityId=");
    builder.append(getActivityId());
    builder.append(", payload=");
    builder.append(getPayload());
    builder.append("]");
    return builder.toString();
  }
}
