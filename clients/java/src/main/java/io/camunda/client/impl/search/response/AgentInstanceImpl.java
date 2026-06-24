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

import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.response.AgentInstance;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.AgentInstanceResult;
import io.camunda.client.protocol.rest.AgentTool;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AgentInstanceImpl implements AgentInstance {

  private final long agentInstanceKey;
  private final AgentInstanceStatus status;
  private final Definition definition;
  private final Metrics metrics;
  private final Limits limits;
  private final List<Tool> tools;
  private final String elementId;
  private final long processInstanceKey;
  private final long rootProcessInstanceKey;
  private final long processDefinitionKey;
  private final String processDefinitionId;
  private final int processDefinitionVersion;
  private final String processDefinitionVersionTag;
  private final String tenantId;
  private final OffsetDateTime creationDate;
  private final OffsetDateTime lastUpdatedDate;
  private final OffsetDateTime completionDate;
  private final List<Long> elementInstanceKeys;

  public AgentInstanceImpl(final AgentInstanceResult result) {
    agentInstanceKey = Long.parseLong(result.getAgentInstanceKey());
    status = EnumUtil.convert(result.getStatus(), AgentInstanceStatus.class);
    definition = new DefinitionImpl(result.getDefinition());
    metrics = new MetricsImpl(result.getMetrics());
    limits = new LimitsImpl(result.getLimits());
    tools =
        result.getTools() != null
            ? result.getTools().stream().map(ToolImpl::new).collect(Collectors.toList())
            : Collections.emptyList();
    elementId = result.getElementId();
    processInstanceKey = Long.parseLong(result.getProcessInstanceKey());
    rootProcessInstanceKey = Long.parseLong(result.getRootProcessInstanceKey());
    processDefinitionKey = Long.parseLong(result.getProcessDefinitionKey());
    processDefinitionId = result.getProcessDefinitionId();
    processDefinitionVersion = result.getProcessDefinitionVersion();
    processDefinitionVersionTag = result.getProcessDefinitionVersionTag();
    tenantId = result.getTenantId();
    creationDate = ParseUtil.parseOffsetDateTimeOrNull(result.getCreationDate());
    lastUpdatedDate = ParseUtil.parseOffsetDateTimeOrNull(result.getLastUpdatedDate());
    completionDate = ParseUtil.parseOffsetDateTimeOrNull(result.getCompletionDate());
    elementInstanceKeys =
        result.getElementInstanceKeys() != null
            ? result.getElementInstanceKeys().stream()
                .map(Long::parseLong)
                .collect(Collectors.toList())
            : Collections.emptyList();
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  @Override
  public AgentInstanceStatus getStatus() {
    return status;
  }

  @Override
  public Definition getDefinition() {
    return definition;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  public Limits getLimits() {
    return limits;
  }

  @Override
  public List<Tool> getTools() {
    return tools;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
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

  @Override
  public OffsetDateTime getCreationDate() {
    return creationDate;
  }

  @Override
  public OffsetDateTime getLastUpdatedDate() {
    return lastUpdatedDate;
  }

  @Override
  public OffsetDateTime getCompletionDate() {
    return completionDate;
  }

  @Override
  public List<Long> getElementInstanceKeys() {
    return elementInstanceKeys;
  }

  private static class DefinitionImpl implements Definition {

    private final String model;
    private final String provider;
    private final String systemPrompt;

    DefinitionImpl(final io.camunda.client.protocol.rest.AgentInstanceDefinition proto) {
      model = proto.getModel();
      provider = proto.getProvider();
      systemPrompt = proto.getSystemPrompt();
    }

    @Override
    public String getModel() {
      return model;
    }

    @Override
    public String getProvider() {
      return provider;
    }

    @Override
    public String getSystemPrompt() {
      return systemPrompt;
    }
  }

  private static class MetricsImpl implements Metrics {

    private final long inputTokens;
    private final long outputTokens;
    private final int modelCalls;
    private final int toolCalls;

    MetricsImpl(final io.camunda.client.protocol.rest.AgentInstanceMetrics proto) {
      inputTokens = proto.getInputTokens();
      outputTokens = proto.getOutputTokens();
      modelCalls = proto.getModelCalls();
      toolCalls = proto.getToolCalls();
    }

    @Override
    public long getInputTokens() {
      return inputTokens;
    }

    @Override
    public long getOutputTokens() {
      return outputTokens;
    }

    @Override
    public int getModelCalls() {
      return modelCalls;
    }

    @Override
    public int getToolCalls() {
      return toolCalls;
    }
  }

  private static class LimitsImpl implements Limits {

    private final int maxModelCalls;
    private final int maxToolCalls;
    private final long maxTokens;

    LimitsImpl(final io.camunda.client.protocol.rest.AgentInstanceLimits proto) {
      maxModelCalls = proto.getMaxModelCalls();
      maxToolCalls = proto.getMaxToolCalls();
      maxTokens = proto.getMaxTokens();
    }

    @Override
    public int getMaxModelCalls() {
      return maxModelCalls;
    }

    @Override
    public int getMaxToolCalls() {
      return maxToolCalls;
    }

    @Override
    public long getMaxTokens() {
      return maxTokens;
    }
  }

  private static class ToolImpl implements Tool {

    private final String name;
    private final String description;
    private final String elementId;

    ToolImpl(final AgentTool proto) {
      name = proto.getName();
      description = proto.getDescription();
      elementId = proto.getElementId();
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String getElementId() {
      return elementId;
    }
  }
}
