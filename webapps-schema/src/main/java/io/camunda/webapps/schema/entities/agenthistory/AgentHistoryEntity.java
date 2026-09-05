/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.agenthistory;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Secondary-storage entity for {@code AGENT_HISTORY} records. Content items and tool calls are
 * stored as nested records on the entity — they are write-only and never individually filtered or
 * sorted. Metrics fields are flattened to top-level fields to avoid nested object complexity in
 * ES/OS.
 */
public final class AgentHistoryEntity
    implements ExporterEntity<AgentHistoryEntity>,
        PartitionedEntity<AgentHistoryEntity>,
        TenantOwned {

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long key;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long agentInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long elementInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long processInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long rootProcessInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String bpmnProcessId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long processDefinitionKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int partitionId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long jobKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String jobLease;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int loopIteration;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private AgentHistoryRole role;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private AgentHistoryCommitStatus commitStatus;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private OffsetDateTime producedAt;

  // Metrics fields — flattened from AgentHistoryMetricsValue
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long inputTokens;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long outputTokens;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long reasoningTokenCount;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long cacheCreationTokenCount;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long cacheReadTokenCount;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long durationMs;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<AgentHistoryContentValue> content;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<AgentHistoryEmbeddedToolCallValue> toolCalls;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String historyItemId;

  /** CONFIGURATION items only — empty when this item didn't touch the tool list. */
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<AgentHistoryToolValue> tools;

  /** CONFIGURATION items only — null if this item didn't touch the model. */
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String model;

  /** CONFIGURATION items only — null if this item didn't touch the provider. */
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String provider;

  /**
   * CONFIGURATION items only — the {@code -1} sentinel ("no limit configured") when this item
   * didn't touch the limits.
   */
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private AgentHistoryLimitsValue limits;

  /** CONFIGURATION items only — empty when this item didn't touch the system prompt. */
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<AgentHistoryContentValue> systemPrompt;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public AgentHistoryEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public AgentHistoryEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public AgentHistoryEntity setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public AgentHistoryEntity setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public AgentHistoryEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public AgentHistoryEntity setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AgentHistoryEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public AgentHistoryEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public AgentHistoryEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public AgentHistoryEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getJobKey() {
    return jobKey;
  }

  public AgentHistoryEntity setJobKey(final long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public String getJobLease() {
    return jobLease;
  }

  public AgentHistoryEntity setJobLease(final String jobLease) {
    this.jobLease = jobLease;
    return this;
  }

  public int getLoopIteration() {
    return loopIteration;
  }

  public AgentHistoryEntity setLoopIteration(final int loopIteration) {
    this.loopIteration = loopIteration;
    return this;
  }

  public AgentHistoryRole getRole() {
    return role;
  }

  public AgentHistoryEntity setRole(final AgentHistoryRole role) {
    this.role = role;
    return this;
  }

  public AgentHistoryCommitStatus getCommitStatus() {
    return commitStatus;
  }

  public AgentHistoryEntity setCommitStatus(final AgentHistoryCommitStatus commitStatus) {
    this.commitStatus = commitStatus;
    return this;
  }

  public OffsetDateTime getProducedAt() {
    return producedAt;
  }

  public AgentHistoryEntity setProducedAt(final OffsetDateTime producedAt) {
    this.producedAt = producedAt;
    return this;
  }

  public Long getInputTokens() {
    return inputTokens;
  }

  public AgentHistoryEntity setInputTokens(final Long inputTokens) {
    this.inputTokens = inputTokens;
    return this;
  }

  public Long getOutputTokens() {
    return outputTokens;
  }

  public AgentHistoryEntity setOutputTokens(final Long outputTokens) {
    this.outputTokens = outputTokens;
    return this;
  }

  public Long getReasoningTokenCount() {
    return reasoningTokenCount;
  }

  public AgentHistoryEntity setReasoningTokenCount(final Long reasoningTokenCount) {
    this.reasoningTokenCount = reasoningTokenCount;
    return this;
  }

  public Long getCacheCreationTokenCount() {
    return cacheCreationTokenCount;
  }

  public AgentHistoryEntity setCacheCreationTokenCount(final Long cacheCreationTokenCount) {
    this.cacheCreationTokenCount = cacheCreationTokenCount;
    return this;
  }

  public Long getCacheReadTokenCount() {
    return cacheReadTokenCount;
  }

  public AgentHistoryEntity setCacheReadTokenCount(final Long cacheReadTokenCount) {
    this.cacheReadTokenCount = cacheReadTokenCount;
    return this;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public AgentHistoryEntity setDurationMs(final Long durationMs) {
    this.durationMs = durationMs;
    return this;
  }

  public List<AgentHistoryContentValue> getContent() {
    return content;
  }

  public AgentHistoryEntity setContent(final List<AgentHistoryContentValue> content) {
    this.content = content;
    return this;
  }

  public List<AgentHistoryEmbeddedToolCallValue> getToolCalls() {
    return toolCalls;
  }

  public AgentHistoryEntity setToolCalls(final List<AgentHistoryEmbeddedToolCallValue> toolCalls) {
    this.toolCalls = toolCalls;
    return this;
  }

  public String getHistoryItemId() {
    return historyItemId;
  }

  public AgentHistoryEntity setHistoryItemId(final String historyItemId) {
    this.historyItemId = historyItemId;
    return this;
  }

  public List<AgentHistoryToolValue> getTools() {
    return tools;
  }

  public AgentHistoryEntity setTools(final List<AgentHistoryToolValue> tools) {
    this.tools = tools;
    return this;
  }

  public String getModel() {
    return model;
  }

  public AgentHistoryEntity setModel(final String model) {
    this.model = model;
    return this;
  }

  public String getProvider() {
    return provider;
  }

  public AgentHistoryEntity setProvider(final String provider) {
    this.provider = provider;
    return this;
  }

  public AgentHistoryLimitsValue getLimits() {
    return limits;
  }

  public AgentHistoryEntity setLimits(final AgentHistoryLimitsValue limits) {
    this.limits = limits;
    return this;
  }

  public List<AgentHistoryContentValue> getSystemPrompt() {
    return systemPrompt;
  }

  public AgentHistoryEntity setSystemPrompt(final List<AgentHistoryContentValue> systemPrompt) {
    this.systemPrompt = systemPrompt;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        agentInstanceKey,
        elementInstanceKey,
        processInstanceKey,
        rootProcessInstanceKey,
        bpmnProcessId,
        processDefinitionKey,
        tenantId,
        partitionId,
        jobKey,
        jobLease,
        loopIteration,
        role,
        commitStatus,
        producedAt,
        inputTokens,
        outputTokens,
        reasoningTokenCount,
        cacheCreationTokenCount,
        cacheReadTokenCount,
        durationMs,
        content,
        toolCalls,
        historyItemId,
        tools,
        model,
        provider,
        limits,
        systemPrompt);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (AgentHistoryEntity) obj;
    return Objects.equals(id, that.id)
        && key == that.key
        && agentInstanceKey == that.agentInstanceKey
        && elementInstanceKey == that.elementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && rootProcessInstanceKey == that.rootProcessInstanceKey
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && processDefinitionKey == that.processDefinitionKey
        && Objects.equals(tenantId, that.tenantId)
        && partitionId == that.partitionId
        && jobKey == that.jobKey
        && Objects.equals(jobLease, that.jobLease)
        && loopIteration == that.loopIteration
        && Objects.equals(role, that.role)
        && Objects.equals(commitStatus, that.commitStatus)
        && Objects.equals(producedAt, that.producedAt)
        && Objects.equals(inputTokens, that.inputTokens)
        && Objects.equals(outputTokens, that.outputTokens)
        && Objects.equals(reasoningTokenCount, that.reasoningTokenCount)
        && Objects.equals(cacheCreationTokenCount, that.cacheCreationTokenCount)
        && Objects.equals(cacheReadTokenCount, that.cacheReadTokenCount)
        && Objects.equals(durationMs, that.durationMs)
        && Objects.equals(content, that.content)
        && Objects.equals(toolCalls, that.toolCalls)
        && Objects.equals(historyItemId, that.historyItemId)
        && Objects.equals(tools, that.tools)
        && Objects.equals(model, that.model)
        && Objects.equals(provider, that.provider)
        && Objects.equals(limits, that.limits)
        && Objects.equals(systemPrompt, that.systemPrompt);
  }

  @Override
  public String toString() {
    return "AgentHistoryEntity{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", agentInstanceKey="
        + agentInstanceKey
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", rootProcessInstanceKey="
        + rootProcessInstanceKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", tenantId='"
        + tenantId
        + '\''
        + ", partitionId="
        + partitionId
        + ", jobKey="
        + jobKey
        + ", jobLease='"
        + jobLease
        + '\''
        + ", loopIteration="
        + loopIteration
        + ", role="
        + role
        + ", commitStatus="
        + commitStatus
        + ", producedAt="
        + producedAt
        + ", inputTokens="
        + inputTokens
        + ", outputTokens="
        + outputTokens
        + ", reasoningTokenCount="
        + reasoningTokenCount
        + ", cacheCreationTokenCount="
        + cacheCreationTokenCount
        + ", cacheReadTokenCount="
        + cacheReadTokenCount
        + ", durationMs="
        + durationMs
        + ", content="
        + content
        + ", toolCalls="
        + toolCalls
        + ", historyItemId='"
        + historyItemId
        + '\''
        + ", tools="
        + tools
        + ", model='"
        + model
        + '\''
        + ", provider='"
        + provider
        + '\''
        + ", limits="
        + limits
        + ", systemPrompt="
        + systemPrompt
        + '}';
  }

  /** A tool call embedded in a history entry. */
  public record AgentHistoryEmbeddedToolCallValue(
      String toolCallId, String toolName, String elementId, Map<String, Object> arguments) {}

  /** A tool made available to the agent by a CONFIGURATION history entry. */
  public record AgentHistoryToolValue(String name, String description, String elementId) {}

  /**
   * The limits carried by a CONFIGURATION history entry. {@code -1} on any field means "no limit
   * configured" for that dimension, same convention as {@code AgentInstanceEntity}'s limits fields.
   */
  public record AgentHistoryLimitsValue(long maxTokens, int maxModelCalls, int maxToolCalls) {}
}
