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
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long totalDurationInMs;
  private Long startDateEpochMs;
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
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(totalDurationInMs, that.totalDurationInMs)
        && Objects.equals(startDateEpochMs, that.startDateEpochMs)
        && Objects.equals(metrics, that.metrics)
        && Objects.equals(tools, that.tools);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentInstanceId,
        flowNodeId,
        processDefinitionVersion,
        startDate,
        endDate,
        totalDurationInMs,
        startDateEpochMs,
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
        + ", startDate="
        + startDate
        + ", endDate="
        + endDate
        + ", totalDurationInMs="
        + totalDurationInMs
        + ", startDateEpochMs="
        + startDateEpochMs
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
    public static final String startDate = "startDate";
    public static final String endDate = "endDate";
    public static final String totalDurationInMs = "totalDurationInMs";
    public static final String startDateEpochMs = "startDateEpochMs";
    public static final String metrics = "metrics";
    public static final String tools = "tools";
  }

  public static class AgentMetricsDto implements Serializable {

    private Long toolCalls;

    public AgentMetricsDto() {}

    public Long getToolCalls() {
      return toolCalls;
    }

    public void setToolCalls(final Long toolCalls) {
      this.toolCalls = toolCalls;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AgentMetricsDto that = (AgentMetricsDto) o;
      return Objects.equals(toolCalls, that.toolCalls);
    }

    @Override
    public int hashCode() {
      return Objects.hash(toolCalls);
    }

    @Override
    public String toString() {
      return "AgentMetricsDto(toolCalls=" + toolCalls + ")";
    }

    @SuppressWarnings("checkstyle:ConstantName")
    public static final class Fields {

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
}
