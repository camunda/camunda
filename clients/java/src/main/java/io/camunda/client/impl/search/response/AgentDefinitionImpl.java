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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.client.api.search.response.AgentDefinition;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AgentDefinitionResult;

public class AgentDefinitionImpl implements AgentDefinition {

  private final long agentDefinitionKey;
  private final AgentDefinitionType agentType;
  private final String name;
  private final String elementId;
  private final String processDefinitionId;
  private final long processDefinitionKey;
  private final int processDefinitionVersion;
  private final String processDefinitionVersionTag;
  private final String tenantId;

  public AgentDefinitionImpl(final AgentDefinitionResult result) {
    agentDefinitionKey = Long.parseLong(result.getAgentDefinitionKey());
    agentType = EnumUtil.convert(result.getAgentType(), AgentDefinitionType.class);
    name = result.getName();
    elementId = result.getElementId();
    processDefinitionId = result.getProcessDefinitionId();
    processDefinitionKey = Long.parseLong(result.getProcessDefinitionKey());
    processDefinitionVersion = result.getProcessDefinitionVersion();
    processDefinitionVersionTag = result.getProcessDefinitionVersionTag();
    tenantId = result.getTenantId();
  }

  @Override
  public long getAgentDefinitionKey() {
    return agentDefinitionKey;
  }

  @Override
  public AgentDefinitionType getAgentType() {
    return agentType;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return processDefinitionVersionTag;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}
