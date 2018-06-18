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
package io.zeebe.client.impl.record;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.client.api.commands.DeploymentResource;
import io.zeebe.client.api.record.DeploymentRecord;
import io.zeebe.client.impl.command.DeploymentResourceImpl;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.event.DeploymentEventImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import java.util.List;

public abstract class DeploymentRecordImpl extends RecordImpl implements DeploymentRecord {
  @JsonProperty("topicName")
  private String deploymentTopic;

  private List<DeploymentResource> resources;

  public DeploymentRecordImpl(ZeebeObjectMapperImpl objectMapper, RecordType recordType) {
    super(objectMapper, recordType, ValueType.DEPLOYMENT);
  }

  @Override
  public List<DeploymentResource> getResources() {
    return resources;
  }

  @JsonDeserialize(contentAs = DeploymentResourceImpl.class)
  public void setResources(List<DeploymentResource> resources) {
    this.resources = resources;
  }

  @Override
  public String getDeploymentTopic() {
    return deploymentTopic;
  }

  public void setDeploymentTopic(String deploymentTopic) {
    this.deploymentTopic = deploymentTopic;
  }

  @Override
  public Class<? extends RecordImpl> getEventClass() {
    return DeploymentEventImpl.class;
  }
}
