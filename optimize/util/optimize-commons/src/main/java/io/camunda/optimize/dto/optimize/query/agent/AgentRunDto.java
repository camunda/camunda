/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agent;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One agent run (one engine agent instance), stored flat in the agent-instance index.
 *
 * <p>Fields that no record has provided yet are null rather than 0, so averages over runs are not
 * skewed by values that arrive later (metrics the runtime did not report, enrichment not yet
 * applied). Identifier naming follows Optimize's C7 convention: {@code ...Id} is a unique key,
 * {@code ...Key} is a BPMN string.
 */
public class AgentRunDto implements OptimizeDto {

  private String agentInstanceId;
  private String agentDefinitionId;
  private String agentId;
  private String flowNodeId;
  private List<String> flowNodeInstanceIds = new ArrayList<>();
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processDefinitionVersion;
  private String processInstanceId;
  private String rootProcessInstanceId;
  private String tenantId;
  private String model;
  private String provider;
  private Long maxModelCalls;
  private Long maxToolCalls;
  private Long maxTokens;
  private OffsetDateTime startDate;
  private OffsetDateTime lastActivityDate;
  private OffsetDateTime endDate;
  private String status;
  private Long durationMs;
  private Long inputTokens;
  private Long outputTokens;
  private Long reasoningTokens;
  private Long cacheCreationTokens;
  private Long cacheReadTokens;
  private Long totalTokens;
  private Long modelCalls;
  private Long toolCalls;
  private Boolean hitLimit;
  private List<String> limitHit = new ArrayList<>();
  private List<String> toolFlowNodeIds = new ArrayList<>();
  private List<ToolCallsDto> toolCallsByTool = new ArrayList<>();
  private List<ModelTokensDto> tokensByModel = new ArrayList<>();
  private String processInstanceState;
  private OffsetDateTime processInstanceEndDate;
  private Boolean hasIncident;
  private Long incidentCount;
  private List<String> incidentFlowNodeIds = new ArrayList<>();
  private Long processUserTaskCount;
  private Long lastRecordPosition;
  private MetricTotalsDto lastMetricTotals;
  private OffsetDateTime enrichedAt;

  public AgentRunDto() {}

  public String getAgentInstanceId() {
    return agentInstanceId;
  }

  public void setAgentInstanceId(final String agentInstanceId) {
    this.agentInstanceId = agentInstanceId;
  }

  public String getAgentDefinitionId() {
    return agentDefinitionId;
  }

  public void setAgentDefinitionId(final String agentDefinitionId) {
    this.agentDefinitionId = agentDefinitionId;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public List<String> getFlowNodeInstanceIds() {
    return flowNodeInstanceIds;
  }

  public void setFlowNodeInstanceIds(final List<String> flowNodeInstanceIds) {
    this.flowNodeInstanceIds = flowNodeInstanceIds;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getRootProcessInstanceId() {
    return rootProcessInstanceId;
  }

  public void setRootProcessInstanceId(final String rootProcessInstanceId) {
    this.rootProcessInstanceId = rootProcessInstanceId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

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

  public Long getMaxModelCalls() {
    return maxModelCalls;
  }

  public void setMaxModelCalls(final Long maxModelCalls) {
    this.maxModelCalls = maxModelCalls;
  }

  public Long getMaxToolCalls() {
    return maxToolCalls;
  }

  public void setMaxToolCalls(final Long maxToolCalls) {
    this.maxToolCalls = maxToolCalls;
  }

  public Long getMaxTokens() {
    return maxTokens;
  }

  public void setMaxTokens(final Long maxTokens) {
    this.maxTokens = maxTokens;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getLastActivityDate() {
    return lastActivityDate;
  }

  public void setLastActivityDate(final OffsetDateTime lastActivityDate) {
    this.lastActivityDate = lastActivityDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(final Long durationMs) {
    this.durationMs = durationMs;
  }

  public Long getInputTokens() {
    return inputTokens;
  }

  public void setInputTokens(final Long inputTokens) {
    this.inputTokens = inputTokens;
  }

  public Long getOutputTokens() {
    return outputTokens;
  }

  public void setOutputTokens(final Long outputTokens) {
    this.outputTokens = outputTokens;
  }

  public Long getReasoningTokens() {
    return reasoningTokens;
  }

  public void setReasoningTokens(final Long reasoningTokens) {
    this.reasoningTokens = reasoningTokens;
  }

  public Long getCacheCreationTokens() {
    return cacheCreationTokens;
  }

  public void setCacheCreationTokens(final Long cacheCreationTokens) {
    this.cacheCreationTokens = cacheCreationTokens;
  }

  public Long getCacheReadTokens() {
    return cacheReadTokens;
  }

  public void setCacheReadTokens(final Long cacheReadTokens) {
    this.cacheReadTokens = cacheReadTokens;
  }

  public Long getTotalTokens() {
    return totalTokens;
  }

  public void setTotalTokens(final Long totalTokens) {
    this.totalTokens = totalTokens;
  }

  public Long getModelCalls() {
    return modelCalls;
  }

  public void setModelCalls(final Long modelCalls) {
    this.modelCalls = modelCalls;
  }

  public Long getToolCalls() {
    return toolCalls;
  }

  public void setToolCalls(final Long toolCalls) {
    this.toolCalls = toolCalls;
  }

  public Boolean getHitLimit() {
    return hitLimit;
  }

  public void setHitLimit(final Boolean hitLimit) {
    this.hitLimit = hitLimit;
  }

  public List<String> getLimitHit() {
    return limitHit;
  }

  public void setLimitHit(final List<String> limitHit) {
    this.limitHit = limitHit;
  }

  public List<String> getToolFlowNodeIds() {
    return toolFlowNodeIds;
  }

  public void setToolFlowNodeIds(final List<String> toolFlowNodeIds) {
    this.toolFlowNodeIds = toolFlowNodeIds;
  }

  public List<ToolCallsDto> getToolCallsByTool() {
    return toolCallsByTool;
  }

  public void setToolCallsByTool(final List<ToolCallsDto> toolCallsByTool) {
    this.toolCallsByTool = toolCallsByTool;
  }

  public List<ModelTokensDto> getTokensByModel() {
    return tokensByModel;
  }

  public void setTokensByModel(final List<ModelTokensDto> tokensByModel) {
    this.tokensByModel = tokensByModel;
  }

  public String getProcessInstanceState() {
    return processInstanceState;
  }

  public void setProcessInstanceState(final String processInstanceState) {
    this.processInstanceState = processInstanceState;
  }

  public OffsetDateTime getProcessInstanceEndDate() {
    return processInstanceEndDate;
  }

  public void setProcessInstanceEndDate(final OffsetDateTime processInstanceEndDate) {
    this.processInstanceEndDate = processInstanceEndDate;
  }

  public Boolean getHasIncident() {
    return hasIncident;
  }

  public void setHasIncident(final Boolean hasIncident) {
    this.hasIncident = hasIncident;
  }

  public Long getIncidentCount() {
    return incidentCount;
  }

  public void setIncidentCount(final Long incidentCount) {
    this.incidentCount = incidentCount;
  }

  public List<String> getIncidentFlowNodeIds() {
    return incidentFlowNodeIds;
  }

  public void setIncidentFlowNodeIds(final List<String> incidentFlowNodeIds) {
    this.incidentFlowNodeIds = incidentFlowNodeIds;
  }

  public Long getProcessUserTaskCount() {
    return processUserTaskCount;
  }

  public void setProcessUserTaskCount(final Long processUserTaskCount) {
    this.processUserTaskCount = processUserTaskCount;
  }

  public Long getLastRecordPosition() {
    return lastRecordPosition;
  }

  public void setLastRecordPosition(final Long lastRecordPosition) {
    this.lastRecordPosition = lastRecordPosition;
  }

  public MetricTotalsDto getLastMetricTotals() {
    return lastMetricTotals;
  }

  public void setLastMetricTotals(final MetricTotalsDto lastMetricTotals) {
    this.lastMetricTotals = lastMetricTotals;
  }

  public OffsetDateTime getEnrichedAt() {
    return enrichedAt;
  }

  public void setEnrichedAt(final OffsetDateTime enrichedAt) {
    this.enrichedAt = enrichedAt;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AgentRunDto that = (AgentRunDto) o;
    return Objects.equals(agentInstanceId, that.agentInstanceId)
        && Objects.equals(agentDefinitionId, that.agentDefinitionId)
        && Objects.equals(agentId, that.agentId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceIds, that.flowNodeInstanceIds)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(processDefinitionVersion, that.processDefinitionVersion)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(rootProcessInstanceId, that.rootProcessInstanceId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(model, that.model)
        && Objects.equals(provider, that.provider)
        && Objects.equals(maxModelCalls, that.maxModelCalls)
        && Objects.equals(maxToolCalls, that.maxToolCalls)
        && Objects.equals(maxTokens, that.maxTokens)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(lastActivityDate, that.lastActivityDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(status, that.status)
        && Objects.equals(durationMs, that.durationMs)
        && Objects.equals(inputTokens, that.inputTokens)
        && Objects.equals(outputTokens, that.outputTokens)
        && Objects.equals(reasoningTokens, that.reasoningTokens)
        && Objects.equals(cacheCreationTokens, that.cacheCreationTokens)
        && Objects.equals(cacheReadTokens, that.cacheReadTokens)
        && Objects.equals(totalTokens, that.totalTokens)
        && Objects.equals(modelCalls, that.modelCalls)
        && Objects.equals(toolCalls, that.toolCalls)
        && Objects.equals(hitLimit, that.hitLimit)
        && Objects.equals(limitHit, that.limitHit)
        && Objects.equals(toolFlowNodeIds, that.toolFlowNodeIds)
        && Objects.equals(toolCallsByTool, that.toolCallsByTool)
        && Objects.equals(tokensByModel, that.tokensByModel)
        && Objects.equals(processInstanceState, that.processInstanceState)
        && Objects.equals(processInstanceEndDate, that.processInstanceEndDate)
        && Objects.equals(hasIncident, that.hasIncident)
        && Objects.equals(incidentCount, that.incidentCount)
        && Objects.equals(incidentFlowNodeIds, that.incidentFlowNodeIds)
        && Objects.equals(processUserTaskCount, that.processUserTaskCount)
        && Objects.equals(lastRecordPosition, that.lastRecordPosition)
        && Objects.equals(lastMetricTotals, that.lastMetricTotals)
        && Objects.equals(enrichedAt, that.enrichedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentInstanceId,
        agentDefinitionId,
        agentId,
        flowNodeId,
        flowNodeInstanceIds,
        processDefinitionKey,
        processDefinitionId,
        processDefinitionVersion,
        processInstanceId,
        rootProcessInstanceId,
        tenantId,
        model,
        provider,
        maxModelCalls,
        maxToolCalls,
        maxTokens,
        startDate,
        lastActivityDate,
        endDate,
        status,
        durationMs,
        inputTokens,
        outputTokens,
        reasoningTokens,
        cacheCreationTokens,
        cacheReadTokens,
        totalTokens,
        modelCalls,
        toolCalls,
        hitLimit,
        limitHit,
        toolFlowNodeIds,
        toolCallsByTool,
        tokensByModel,
        processInstanceState,
        processInstanceEndDate,
        hasIncident,
        incidentCount,
        incidentFlowNodeIds,
        processUserTaskCount,
        lastRecordPosition,
        lastMetricTotals,
        enrichedAt);
  }

  @Override
  public String toString() {
    return "AgentRunDto("
        + "agentInstanceId="
        + agentInstanceId
        + ", agentDefinitionId="
        + agentDefinitionId
        + ", agentId="
        + agentId
        + ", flowNodeId="
        + flowNodeId
        + ", flowNodeInstanceIds="
        + flowNodeInstanceIds
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionId="
        + processDefinitionId
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", processInstanceId="
        + processInstanceId
        + ", rootProcessInstanceId="
        + rootProcessInstanceId
        + ", tenantId="
        + tenantId
        + ", model="
        + model
        + ", provider="
        + provider
        + ", maxModelCalls="
        + maxModelCalls
        + ", maxToolCalls="
        + maxToolCalls
        + ", maxTokens="
        + maxTokens
        + ", startDate="
        + startDate
        + ", lastActivityDate="
        + lastActivityDate
        + ", endDate="
        + endDate
        + ", status="
        + status
        + ", durationMs="
        + durationMs
        + ", inputTokens="
        + inputTokens
        + ", outputTokens="
        + outputTokens
        + ", reasoningTokens="
        + reasoningTokens
        + ", cacheCreationTokens="
        + cacheCreationTokens
        + ", cacheReadTokens="
        + cacheReadTokens
        + ", totalTokens="
        + totalTokens
        + ", modelCalls="
        + modelCalls
        + ", toolCalls="
        + toolCalls
        + ", hitLimit="
        + hitLimit
        + ", limitHit="
        + limitHit
        + ", toolFlowNodeIds="
        + toolFlowNodeIds
        + ", toolCallsByTool="
        + toolCallsByTool
        + ", tokensByModel="
        + tokensByModel
        + ", processInstanceState="
        + processInstanceState
        + ", processInstanceEndDate="
        + processInstanceEndDate
        + ", hasIncident="
        + hasIncident
        + ", incidentCount="
        + incidentCount
        + ", incidentFlowNodeIds="
        + incidentFlowNodeIds
        + ", processUserTaskCount="
        + processUserTaskCount
        + ", lastRecordPosition="
        + lastRecordPosition
        + ", lastMetricTotals="
        + lastMetricTotals
        + ", enrichedAt="
        + enrichedAt
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String agentInstanceId = "agentInstanceId";
    public static final String agentDefinitionId = "agentDefinitionId";
    public static final String agentId = "agentId";
    public static final String flowNodeId = "flowNodeId";
    public static final String flowNodeInstanceIds = "flowNodeInstanceIds";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processDefinitionId = "processDefinitionId";
    public static final String processDefinitionVersion = "processDefinitionVersion";
    public static final String processInstanceId = "processInstanceId";
    public static final String rootProcessInstanceId = "rootProcessInstanceId";
    public static final String tenantId = "tenantId";
    public static final String model = "model";
    public static final String provider = "provider";
    public static final String maxModelCalls = "maxModelCalls";
    public static final String maxToolCalls = "maxToolCalls";
    public static final String maxTokens = "maxTokens";
    public static final String startDate = "startDate";
    public static final String lastActivityDate = "lastActivityDate";
    public static final String endDate = "endDate";
    public static final String status = "status";
    public static final String durationMs = "durationMs";
    public static final String inputTokens = "inputTokens";
    public static final String outputTokens = "outputTokens";
    public static final String reasoningTokens = "reasoningTokens";
    public static final String cacheCreationTokens = "cacheCreationTokens";
    public static final String cacheReadTokens = "cacheReadTokens";
    public static final String totalTokens = "totalTokens";
    public static final String modelCalls = "modelCalls";
    public static final String toolCalls = "toolCalls";
    public static final String hitLimit = "hitLimit";
    public static final String limitHit = "limitHit";
    public static final String toolFlowNodeIds = "toolFlowNodeIds";
    public static final String toolCallsByTool = "toolCallsByTool";
    public static final String tokensByModel = "tokensByModel";
    public static final String processInstanceState = "processInstanceState";
    public static final String processInstanceEndDate = "processInstanceEndDate";
    public static final String hasIncident = "hasIncident";
    public static final String incidentCount = "incidentCount";
    public static final String incidentFlowNodeIds = "incidentFlowNodeIds";
    public static final String processUserTaskCount = "processUserTaskCount";
    public static final String lastRecordPosition = "lastRecordPosition";
    public static final String lastMetricTotals = "lastMetricTotals";
    public static final String enrichedAt = "enrichedAt";
  }

  /** Number of flow node instances of one tool element in the run's process instance. */
  public static class ToolCallsDto {

    private String toolFlowNodeId;
    private String toolName;
    private Long count;

    public ToolCallsDto() {}

    public String getToolFlowNodeId() {
      return toolFlowNodeId;
    }

    public void setToolFlowNodeId(final String toolFlowNodeId) {
      this.toolFlowNodeId = toolFlowNodeId;
    }

    public String getToolName() {
      return toolName;
    }

    public void setToolName(final String toolName) {
      this.toolName = toolName;
    }

    public Long getCount() {
      return count;
    }

    public void setCount(final Long count) {
      this.count = count;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ToolCallsDto that = (ToolCallsDto) o;
      return Objects.equals(toolFlowNodeId, that.toolFlowNodeId)
          && Objects.equals(toolName, that.toolName)
          && Objects.equals(count, that.count);
    }

    @Override
    public int hashCode() {
      return Objects.hash(toolFlowNodeId, toolName, count);
    }

    @Override
    public String toString() {
      return "ToolCallsDto("
          + "toolFlowNodeId="
          + toolFlowNodeId
          + ", toolName="
          + toolName
          + ", count="
          + count
          + ")";
    }

    @SuppressWarnings("checkstyle:ConstantName")
    public static final class Fields {

      public static final String toolFlowNodeId = "toolFlowNodeId";
      public static final String toolName = "toolName";
      public static final String count = "count";
    }
  }

  /** Tokens a run spent while a given provider/model was in effect. */
  public static class ModelTokensDto {

    private String provider;
    private String model;
    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long cacheCreationTokens;
    private Long cacheReadTokens;
    private Long totalTokens;

    public ModelTokensDto() {}

    public String getProvider() {
      return provider;
    }

    public void setProvider(final String provider) {
      this.provider = provider;
    }

    public String getModel() {
      return model;
    }

    public void setModel(final String model) {
      this.model = model;
    }

    public Long getInputTokens() {
      return inputTokens;
    }

    public void setInputTokens(final Long inputTokens) {
      this.inputTokens = inputTokens;
    }

    public Long getOutputTokens() {
      return outputTokens;
    }

    public void setOutputTokens(final Long outputTokens) {
      this.outputTokens = outputTokens;
    }

    public Long getReasoningTokens() {
      return reasoningTokens;
    }

    public void setReasoningTokens(final Long reasoningTokens) {
      this.reasoningTokens = reasoningTokens;
    }

    public Long getCacheCreationTokens() {
      return cacheCreationTokens;
    }

    public void setCacheCreationTokens(final Long cacheCreationTokens) {
      this.cacheCreationTokens = cacheCreationTokens;
    }

    public Long getCacheReadTokens() {
      return cacheReadTokens;
    }

    public void setCacheReadTokens(final Long cacheReadTokens) {
      this.cacheReadTokens = cacheReadTokens;
    }

    public Long getTotalTokens() {
      return totalTokens;
    }

    public void setTotalTokens(final Long totalTokens) {
      this.totalTokens = totalTokens;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ModelTokensDto that = (ModelTokensDto) o;
      return Objects.equals(provider, that.provider)
          && Objects.equals(model, that.model)
          && Objects.equals(inputTokens, that.inputTokens)
          && Objects.equals(outputTokens, that.outputTokens)
          && Objects.equals(reasoningTokens, that.reasoningTokens)
          && Objects.equals(cacheCreationTokens, that.cacheCreationTokens)
          && Objects.equals(cacheReadTokens, that.cacheReadTokens)
          && Objects.equals(totalTokens, that.totalTokens);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          provider,
          model,
          inputTokens,
          outputTokens,
          reasoningTokens,
          cacheCreationTokens,
          cacheReadTokens,
          totalTokens);
    }

    @Override
    public String toString() {
      return "ModelTokensDto("
          + "provider="
          + provider
          + ", model="
          + model
          + ", inputTokens="
          + inputTokens
          + ", outputTokens="
          + outputTokens
          + ", reasoningTokens="
          + reasoningTokens
          + ", cacheCreationTokens="
          + cacheCreationTokens
          + ", cacheReadTokens="
          + cacheReadTokens
          + ", totalTokens="
          + totalTokens
          + ")";
    }

    @SuppressWarnings("checkstyle:ConstantName")
    public static final class Fields {

      public static final String provider = "provider";
      public static final String model = "model";
      public static final String inputTokens = "inputTokens";
      public static final String outputTokens = "outputTokens";
      public static final String reasoningTokens = "reasoningTokens";
      public static final String cacheCreationTokens = "cacheCreationTokens";
      public static final String cacheReadTokens = "cacheReadTokens";
      public static final String totalTokens = "totalTokens";
    }
  }

  /** Running metric totals of the last record folded in, used to derive per-model deltas. */
  public static class MetricTotalsDto {

    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long cacheCreationTokens;
    private Long cacheReadTokens;

    public MetricTotalsDto() {}

    public Long getInputTokens() {
      return inputTokens;
    }

    public void setInputTokens(final Long inputTokens) {
      this.inputTokens = inputTokens;
    }

    public Long getOutputTokens() {
      return outputTokens;
    }

    public void setOutputTokens(final Long outputTokens) {
      this.outputTokens = outputTokens;
    }

    public Long getReasoningTokens() {
      return reasoningTokens;
    }

    public void setReasoningTokens(final Long reasoningTokens) {
      this.reasoningTokens = reasoningTokens;
    }

    public Long getCacheCreationTokens() {
      return cacheCreationTokens;
    }

    public void setCacheCreationTokens(final Long cacheCreationTokens) {
      this.cacheCreationTokens = cacheCreationTokens;
    }

    public Long getCacheReadTokens() {
      return cacheReadTokens;
    }

    public void setCacheReadTokens(final Long cacheReadTokens) {
      this.cacheReadTokens = cacheReadTokens;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final MetricTotalsDto that = (MetricTotalsDto) o;
      return Objects.equals(inputTokens, that.inputTokens)
          && Objects.equals(outputTokens, that.outputTokens)
          && Objects.equals(reasoningTokens, that.reasoningTokens)
          && Objects.equals(cacheCreationTokens, that.cacheCreationTokens)
          && Objects.equals(cacheReadTokens, that.cacheReadTokens);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          inputTokens, outputTokens, reasoningTokens, cacheCreationTokens, cacheReadTokens);
    }

    @Override
    public String toString() {
      return "MetricTotalsDto("
          + "inputTokens="
          + inputTokens
          + ", outputTokens="
          + outputTokens
          + ", reasoningTokens="
          + reasoningTokens
          + ", cacheCreationTokens="
          + cacheCreationTokens
          + ", cacheReadTokens="
          + cacheReadTokens
          + ")";
    }

    @SuppressWarnings("checkstyle:ConstantName")
    public static final class Fields {

      public static final String inputTokens = "inputTokens";
      public static final String outputTokens = "outputTokens";
      public static final String reasoningTokens = "reasoningTokens";
      public static final String cacheCreationTokens = "cacheCreationTokens";
      public static final String cacheReadTokens = "cacheReadTokens";
    }
  }
}
