/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AgentInstanceDto implements Serializable, OptimizeDto {

  private String agentInstanceId;
  private String flowNodeId;
  private String processDefinitionVersion;
  private String status;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private OffsetDateTime lastUpdatedDate;
  private Long totalDurationInMs;
  private Long startDateEpochMs;
  private Long endDateEpochMs;
  private List<String> elementInstanceKeys = new ArrayList<>();
  private AgentDefinitionDto definition = new AgentDefinitionDto();
  private AgentMetricsDto metrics = new AgentMetricsDto();
  private List<AgentToolDto> tools = new ArrayList<>();

  public AgentInstanceDto() {}

  public String getAgentInstanceId() {
    return agentInstanceId;
  }

  public void setAgentInstanceId(final String agentInstanceId) {
    this.agentInstanceId = agentInstanceId;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public OffsetDateTime getLastUpdatedDate() {
    return lastUpdatedDate;
  }

  public void setLastUpdatedDate(final OffsetDateTime lastUpdatedDate) {
    this.lastUpdatedDate = lastUpdatedDate;
  }

  public Long getTotalDurationInMs() {
    return totalDurationInMs;
  }

  public void setTotalDurationInMs(final Long totalDurationInMs) {
    this.totalDurationInMs = totalDurationInMs;
  }

  public Long getStartDateEpochMs() {
    return startDateEpochMs;
  }

  public void setStartDateEpochMs(final Long startDateEpochMs) {
    this.startDateEpochMs = startDateEpochMs;
  }

  public Long getEndDateEpochMs() {
    return endDateEpochMs;
  }

  public void setEndDateEpochMs(final Long endDateEpochMs) {
    this.endDateEpochMs = endDateEpochMs;
  }

  public List<String> getElementInstanceKeys() {
    return elementInstanceKeys;
  }

  public void setElementInstanceKeys(final List<String> elementInstanceKeys) {
    this.elementInstanceKeys = elementInstanceKeys;
  }

  public AgentDefinitionDto getDefinition() {
    return definition;
  }

  public void setDefinition(final AgentDefinitionDto definition) {
    this.definition = definition;
  }

  public AgentMetricsDto getMetrics() {
    return metrics;
  }

  public void setMetrics(final AgentMetricsDto metrics) {
    this.metrics = metrics;
  }

  public List<AgentToolDto> getTools() {
    return tools;
  }

  public void setTools(final List<AgentToolDto> tools) {
    this.tools = tools;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AgentInstanceDto that = (AgentInstanceDto) o;
    return Objects.equals(agentInstanceId, that.agentInstanceId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(processDefinitionVersion, that.processDefinitionVersion)
        && Objects.equals(status, that.status)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(lastUpdatedDate, that.lastUpdatedDate)
        && Objects.equals(totalDurationInMs, that.totalDurationInMs)
        && Objects.equals(startDateEpochMs, that.startDateEpochMs)
        && Objects.equals(endDateEpochMs, that.endDateEpochMs)
        && Objects.equals(elementInstanceKeys, that.elementInstanceKeys)
        && Objects.equals(definition, that.definition)
        && Objects.equals(metrics, that.metrics)
        && Objects.equals(tools, that.tools);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentInstanceId,
        flowNodeId,
        processDefinitionVersion,
        status,
        startDate,
        endDate,
        lastUpdatedDate,
        totalDurationInMs,
        startDateEpochMs,
        endDateEpochMs,
        elementInstanceKeys,
        definition,
        metrics,
        tools);
  }

  @Override
  public String toString() {
    return "AgentInstanceDto(agentInstanceId="
        + agentInstanceId
        + ", flowNodeId="
        + flowNodeId
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", status="
        + status
        + ", startDate="
        + startDate
        + ", endDate="
        + endDate
        + ", lastUpdatedDate="
        + lastUpdatedDate
        + ", totalDurationInMs="
        + totalDurationInMs
        + ", startDateEpochMs="
        + startDateEpochMs
        + ", endDateEpochMs="
        + endDateEpochMs
        + ", elementInstanceKeys="
        + elementInstanceKeys
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

    public static final String agentInstanceId = "agentInstanceId";
    public static final String flowNodeId = "flowNodeId";
    public static final String processDefinitionVersion = "processDefinitionVersion";
    public static final String status = "status";
    public static final String startDate = "startDate";
    public static final String endDate = "endDate";
    public static final String lastUpdatedDate = "lastUpdatedDate";
    public static final String totalDurationInMs = "totalDurationInMs";
    public static final String startDateEpochMs = "startDateEpochMs";
    public static final String endDateEpochMs = "endDateEpochMs";
    public static final String elementInstanceKeys = "elementInstanceKeys";
    public static final String definition = "definition";
    public static final String metrics = "metrics";
    public static final String tools = "tools";
  }

  public static class AgentMetricsDto implements Serializable {

    private long inputTokens;
    private long outputTokens;
    private long modelCalls;
    private long toolCalls;

    public AgentMetricsDto() {}

    public long getInputTokens() {
      return inputTokens;
    }

    public void setInputTokens(final long inputTokens) {
      this.inputTokens = inputTokens;
    }

    public long getOutputTokens() {
      return outputTokens;
    }

    public void setOutputTokens(final long outputTokens) {
      this.outputTokens = outputTokens;
    }

    public long getModelCalls() {
      return modelCalls;
    }

    public void setModelCalls(final long modelCalls) {
      this.modelCalls = modelCalls;
    }

    public long getToolCalls() {
      return toolCalls;
    }

    public void setToolCalls(final long toolCalls) {
      this.toolCalls = toolCalls;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentMetricsDto that = (AgentMetricsDto) o;
      return inputTokens == that.inputTokens
          && outputTokens == that.outputTokens
          && modelCalls == that.modelCalls
          && toolCalls == that.toolCalls;
    }

    @Override
    public int hashCode() {
      return Objects.hash(inputTokens, outputTokens, modelCalls, toolCalls);
    }

    @Override
    public String toString() {
      return "AgentMetricsDto(inputTokens="
          + inputTokens
          + ", outputTokens="
          + outputTokens
          + ", modelCalls="
          + modelCalls
          + ", toolCalls="
          + toolCalls
          + ")";
    }

    @SuppressWarnings("checkstyle:ConstantName")
    public static final class Fields {

      public static final String inputTokens = "inputTokens";
      public static final String outputTokens = "outputTokens";
      public static final String modelCalls = "modelCalls";
      public static final String toolCalls = "toolCalls";
    }
  }

  public static class AgentToolDto implements Serializable {

    private String name;

    public AgentToolDto() {}

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentToolDto that = (AgentToolDto) o;
      return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }

    @Override
    public String toString() {
      return "AgentToolDto(name=" + name + ")";
    }

    @SuppressWarnings("checkstyle:ConstantName")
    public static final class Fields {

      public static final String name = "name";
    }
  }

  public static class AgentDefinitionDto implements Serializable {

    private String model;
    private String provider;

    public AgentDefinitionDto() {}

    public String getModel() {
      return model;
    }

    public void setModel(final String model) {
      this.model = model;
    }

    public String getProvider() {
      return provider;
    }

    public void setProvider(final String provider) {
      this.provider = provider;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentDefinitionDto that = (AgentDefinitionDto) o;
      return Objects.equals(model, that.model) && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
      return Objects.hash(model, provider);
    }

    @Override
    public String toString() {
      return "AgentDefinitionDto(model=" + model + ", provider=" + provider + ")";
    }

    @SuppressWarnings("checkstyle:ConstantName")
    public static final class Fields {

      public static final String model = "model";
      public static final String provider = "provider";
    }
  }
}
