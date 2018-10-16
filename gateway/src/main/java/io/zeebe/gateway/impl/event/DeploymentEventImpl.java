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
package io.zeebe.gateway.impl.event;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.gateway.api.commands.Workflow;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.DeploymentState;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.gateway.impl.record.DeploymentRecordImpl;
import io.zeebe.protocol.clientapi.RecordType;
import java.util.List;

public class DeploymentEventImpl extends DeploymentRecordImpl implements DeploymentEvent {
  private List<Workflow> workflows;

  @JsonCreator
  public DeploymentEventImpl(@JacksonInject final ZeebeObjectMapperImpl objectMapper) {
    super(objectMapper, RecordType.EVENT);
  }

  @Override
  @JsonDeserialize(contentAs = WorkflowImpl.class)
  public List<Workflow> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(final List<Workflow> deployedWorkflows) {
    this.workflows = deployedWorkflows;
  }

  @JsonIgnore
  @Override
  public DeploymentState getState() {
    return DeploymentState.valueOf(getMetadata().getIntent());
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("DeploymentEvent [state=");
    builder.append(getState());
    builder.append(", resource=");
    builder.append(getResources());
    builder.append(", workflows=");
    builder.append(workflows);
    builder.append("]");
    return builder.toString();
  }
}
