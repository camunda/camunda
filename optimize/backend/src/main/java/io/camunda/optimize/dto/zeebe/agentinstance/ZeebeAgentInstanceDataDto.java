/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.agentinstance;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

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
  private AgentInstanceStatus status;
  private AgentDefinitionValueDto definition = new AgentDefinitionValueDto();
  private AgentMetricsValueDto metrics = new AgentMetricsValueDto();
  private List<AgentToolValueDto> tools = new ArrayList<>();

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

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  @Override
  public List<Long> getElementInstanceKeys() {
    return elementInstanceKeys;
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
  public String getVersionTag() {
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
    return new ArrayList<>(tools);
  }

  public void setTools(final List<AgentToolValueDto> tools) {
    this.tools = tools;
  }

  @Override
  public List<String> getChangedAttributes() {
    return List.of();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentInstanceKey,
        elementInstanceKey,
        elementId,
        processInstanceKey,
        rootProcessInstanceKey,
        bpmnProcessId,
        processDefinitionKey,
        processDefinitionVersion,
        tenantId,
        status,
        definition,
        metrics,
        tools);
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
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(status, that.status)
        && Objects.equals(definition, that.definition)
        && Objects.equals(metrics, that.metrics)
        && Objects.equals(tools, that.tools);
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
        + ", status="
        + status
        + ", definition="
        + definition
        + ", metrics="
        + metrics
        + ", tools="
        + tools
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
    public static final String status = "status";
    public static final String definition = "definition";
    public static final String metrics = "metrics";
    public static final String tools = "tools";
  }

  public static class AgentDefinitionValueDto implements AgentInstanceDefinitionValue {

    private String model = "";
    private String provider = "";
    private String systemPrompt = "";

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
    public String getSystemPrompt() {
      return systemPrompt;
    }

    public void setSystemPrompt(final String systemPrompt) {
      this.systemPrompt = systemPrompt;
    }

    @Override
    public int hashCode() {
      return Objects.hash(model, provider, systemPrompt);
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentDefinitionValueDto that = (AgentDefinitionValueDto) o;
      return Objects.equals(model, that.model)
          && Objects.equals(provider, that.provider)
          && Objects.equals(systemPrompt, that.systemPrompt);
    }

    @Override
    public String toString() {
      return "AgentDefinitionValueDto(model=" + model + ", provider=" + provider + ")";
    }
  }

  public static class AgentMetricsValueDto implements AgentInstanceMetricsValue {

    private long inputTokens;
    private long outputTokens;
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
      return Objects.hash(inputTokens, outputTokens, modelCalls, toolCalls);
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentMetricsValueDto that = (AgentMetricsValueDto) o;
      return inputTokens == that.inputTokens
          && outputTokens == that.outputTokens
          && modelCalls == that.modelCalls
          && toolCalls == that.toolCalls;
    }

    @Override
    public String toString() {
      return "AgentMetricsValueDto(inputTokens="
          + inputTokens
          + ", outputTokens="
          + outputTokens
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
