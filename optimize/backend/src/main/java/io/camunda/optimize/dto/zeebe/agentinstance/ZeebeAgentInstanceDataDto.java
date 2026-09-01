/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.agentinstance;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class ZeebeAgentInstanceDataDto implements AgentInstanceRecordValue {

  private long agentInstanceKey;
  private long elementInstanceKey;
  private List<Long> elementInstanceKeys = new ArrayList<>();
  private String elementId;
  private long processInstanceKey;
  private long rootProcessInstanceKey;
  private String bpmnProcessId;
  private long processDefinitionKey;
  private int processDefinitionVersion;
  private String tenantId;
  private long jobKey = -1L;
  private String jobLease;
  private AgentInstanceStatus status;
  private AgentDefinitionValueDto definition = new AgentDefinitionValueDto();
  private AgentMetricsValueDto metrics = new AgentMetricsValueDto();
  private List<AgentToolValueDto> tools = new ArrayList<>();
  private List<String> changedAttributes = new ArrayList<>();

  public ZeebeAgentInstanceDataDto() {}

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public void setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
  }

  // Not tracked — Optimize's agent-instance import doesn't use the link back to the agent
  // definition, so this identity is intentionally left unset rather than deserialized.
  @Override
  public long getAgentDefinitionKey() {
    return -1;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  @Override
  public List<Long> getElementInstanceKeys() {
    return elementInstanceKeys != null ? elementInstanceKeys : List.of();
  }

  public void setElementInstanceKeys(final List<Long> elementInstanceKeys) {
    this.elementInstanceKeys = elementInstanceKeys;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public int getStorageOrdinalKey() {
    return -1; // not used in Optimize
  }

  public void setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return null;
  }

  @Override
  public long getJobKey() {
    return jobKey;
  }

  public void setJobKey(final long jobKey) {
    this.jobKey = jobKey;
  }

  @Override
  public String getJobLease() {
    return jobLease;
  }

  public void setJobLease(final String jobLease) {
    this.jobLease = jobLease;
  }

  // Not tracked — Optimize's import doesn't need the embedded history batch, only the
  // instance-level fields above.
  @Override
  public List<AgentHistoryRecordValue> getHistory() {
    return null;
  }

  @Override
  public String getTenantId() {
    return StringUtils.isEmpty(tenantId) ? ZEEBE_DEFAULT_TENANT_ID : tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public AgentInstanceStatus getStatus() {
    return status;
  }

  public void setStatus(final AgentInstanceStatus status) {
    this.status = status;
  }

  @Override
  public AgentDefinitionValueDto getDefinition() {
    return definition;
  }

  public void setDefinition(final AgentDefinitionValueDto definition) {
    this.definition = definition;
  }

  @Override
  public AgentInstanceLimitsValue getLimits() {
    return null;
  }

  @Override
  public AgentMetricsValueDto getMetrics() {
    return metrics;
  }

  public void setMetrics(final AgentMetricsValueDto metrics) {
    this.metrics = metrics;
  }

  @Override
  public List<AgentInstanceToolValue> getTools() {
    return tools != null ? new ArrayList<>(tools) : new ArrayList<>();
  }

  public void setTools(final List<AgentToolValueDto> tools) {
    this.tools = tools;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributes != null ? changedAttributes : List.of();
  }

  public void setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentInstanceKey,
        elementInstanceKey,
        elementInstanceKeys,
        elementId,
        processInstanceKey,
        rootProcessInstanceKey,
        bpmnProcessId,
        processDefinitionKey,
        processDefinitionVersion,
        tenantId,
        jobKey,
        jobLease,
        status,
        definition,
        metrics,
        tools,
        changedAttributes);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeAgentInstanceDataDto that = (ZeebeAgentInstanceDataDto) o;
    return agentInstanceKey == that.agentInstanceKey
        && elementInstanceKey == that.elementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && rootProcessInstanceKey == that.rootProcessInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && jobKey == that.jobKey
        && Objects.equals(elementInstanceKeys, that.elementInstanceKeys)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(jobLease, that.jobLease)
        && Objects.equals(status, that.status)
        && Objects.equals(definition, that.definition)
        && Objects.equals(metrics, that.metrics)
        && Objects.equals(tools, that.tools)
        && Objects.equals(changedAttributes, that.changedAttributes);
  }

  @Override
  public String toString() {
    return "ZeebeAgentInstanceDataDto(agentInstanceKey="
        + agentInstanceKey
        + ", elementId="
        + elementId
        + ", processInstanceKey="
        + processInstanceKey
        + ", rootProcessInstanceKey="
        + rootProcessInstanceKey
        + ", bpmnProcessId="
        + bpmnProcessId
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", tenantId="
        + tenantId
        + ", jobKey="
        + jobKey
        + ", jobLease="
        + jobLease
        + ", status="
        + status
        + ", definition="
        + definition
        + ", metrics="
        + metrics
        + ", tools="
        + tools
        + ", changedAttributes="
        + changedAttributes
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String agentInstanceKey = "agentInstanceKey";
    public static final String elementInstanceKey = "elementInstanceKey";
    public static final String elementId = "elementId";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String rootProcessInstanceKey = "rootProcessInstanceKey";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processDefinitionVersion = "processDefinitionVersion";
    public static final String tenantId = "tenantId";
    public static final String jobKey = "jobKey";
    public static final String jobLease = "jobLease";
    public static final String status = "status";
    public static final String definition = "definition";
    public static final String metrics = "metrics";
    public static final String tools = "tools";
    public static final String changedAttributes = "changedAttributes";
  }

  public static class AgentDefinitionValueDto implements AgentInstanceDefinitionValue {

    private String model = "";
    private String provider = "";

    public AgentDefinitionValueDto() {}

    @Override
    public String getModel() {
      return model;
    }

    public void setModel(final String model) {
      this.model = model;
    }

    @Override
    public String getProvider() {
      return provider;
    }

    public void setProvider(final String provider) {
      this.provider = provider;
    }

    @Override
    @JsonIgnore
    public List<AgentHistoryRecordValue.AgentHistoryMessageContentValue> getSystemPrompt() {
      // Optimize does not use the system prompt content, so it is never populated here. Ignored
      // by Jackson so deserializing a real record with a non-empty array doesn't try to mutate
      // this immutable list.
      return List.of();
    }

    @Override
    public int hashCode() {
      return Objects.hash(model, provider);
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentDefinitionValueDto that = (AgentDefinitionValueDto) o;
      return Objects.equals(model, that.model) && Objects.equals(provider, that.provider);
    }

    @Override
    public String toString() {
      return "AgentDefinitionValueDto(model=" + model + ", provider=" + provider + ")";
    }
  }

  public static class AgentMetricsValueDto implements AgentInstanceMetricsValue {

    private long inputTokens;
    private long outputTokens;
    private long reasoningTokenCount;
    private long cacheCreationTokenCount;
    private long cacheReadTokenCount;
    private int modelCalls;
    private int toolCalls;

    public AgentMetricsValueDto() {}

    @Override
    public long getInputTokens() {
      return inputTokens;
    }

    public void setInputTokens(final long inputTokens) {
      this.inputTokens = inputTokens;
    }

    @Override
    public long getOutputTokens() {
      return outputTokens;
    }

    public void setOutputTokens(final long outputTokens) {
      this.outputTokens = outputTokens;
    }

    @Override
    public long getReasoningTokenCount() {
      return reasoningTokenCount;
    }

    public void setReasoningTokenCount(final long reasoningTokenCount) {
      this.reasoningTokenCount = reasoningTokenCount;
    }

    @Override
    public long getCacheCreationTokenCount() {
      return cacheCreationTokenCount;
    }

    public void setCacheCreationTokenCount(final long cacheCreationTokenCount) {
      this.cacheCreationTokenCount = cacheCreationTokenCount;
    }

    @Override
    public long getCacheReadTokenCount() {
      return cacheReadTokenCount;
    }

    public void setCacheReadTokenCount(final long cacheReadTokenCount) {
      this.cacheReadTokenCount = cacheReadTokenCount;
    }

    @Override
    public int getModelCalls() {
      return modelCalls;
    }

    public void setModelCalls(final int modelCalls) {
      this.modelCalls = modelCalls;
    }

    @Override
    public int getToolCalls() {
      return toolCalls;
    }

    public void setToolCalls(final int toolCalls) {
      this.toolCalls = toolCalls;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          inputTokens,
          outputTokens,
          reasoningTokenCount,
          cacheCreationTokenCount,
          cacheReadTokenCount,
          modelCalls,
          toolCalls);
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentMetricsValueDto that = (AgentMetricsValueDto) o;
      return inputTokens == that.inputTokens
          && outputTokens == that.outputTokens
          && reasoningTokenCount == that.reasoningTokenCount
          && cacheCreationTokenCount == that.cacheCreationTokenCount
          && cacheReadTokenCount == that.cacheReadTokenCount
          && modelCalls == that.modelCalls
          && toolCalls == that.toolCalls;
    }

    @Override
    public String toString() {
      return "AgentMetricsValueDto(inputTokens="
          + inputTokens
          + ", outputTokens="
          + outputTokens
          + ", reasoningTokenCount="
          + reasoningTokenCount
          + ", cacheCreationTokenCount="
          + cacheCreationTokenCount
          + ", cacheReadTokenCount="
          + cacheReadTokenCount
          + ", modelCalls="
          + modelCalls
          + ", toolCalls="
          + toolCalls
          + ")";
    }
  }

  public static class AgentToolValueDto implements AgentInstanceToolValue {

    private String name;
    private String description;
    private String elementId;

    public AgentToolValueDto() {}

    @Override
    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    public void setDescription(final String description) {
      this.description = description;
    }

    @Override
    public String getElementId() {
      return elementId;
    }

    public void setElementId(final String elementId) {
      this.elementId = elementId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, description, elementId);
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentToolValueDto that = (AgentToolValueDto) o;
      return Objects.equals(name, that.name)
          && Objects.equals(description, that.description)
          && Objects.equals(elementId, that.elementId);
    }

    @Override
    public String toString() {
      return "AgentToolValueDto(name=" + name + ", description=" + description + ")";
    }
  }
}
