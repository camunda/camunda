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
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
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
  private long processDefinitionKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int partitionId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long jobKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String jobLease;

  /** Nullable — protocol value {@code <= 0} is stored as {@code null}. */
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Integer iteration;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private AgentHistoryRole role;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private AgentHistoryCommitStatus commitStatus;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private OffsetDateTime producedAt;

  // Metrics fields — flattened from AgentHistoryMetricsValue
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long inputTokens;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long outputTokens;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long durationMs;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<AgentHistoryContentValue> content;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<AgentHistoryEmbeddedToolCallValue> toolCalls;

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

  public Integer getIteration() {
    return iteration;
  }

  public AgentHistoryEntity setIteration(final Integer iteration) {
    this.iteration = iteration;
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

  public long getInputTokens() {
    return inputTokens;
  }

  public AgentHistoryEntity setInputTokens(final long inputTokens) {
    this.inputTokens = inputTokens;
    return this;
  }

  public long getOutputTokens() {
    return outputTokens;
  }

  public AgentHistoryEntity setOutputTokens(final long outputTokens) {
    this.outputTokens = outputTokens;
    return this;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public AgentHistoryEntity setDurationMs(final long durationMs) {
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

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        agentInstanceKey,
        elementInstanceKey,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionKey,
        tenantId,
        partitionId,
        jobKey,
        jobLease,
        iteration,
        role,
        commitStatus,
        producedAt,
        inputTokens,
        outputTokens,
        durationMs,
        content,
        toolCalls);
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
        && processDefinitionKey == that.processDefinitionKey
        && Objects.equals(tenantId, that.tenantId)
        && partitionId == that.partitionId
        && jobKey == that.jobKey
        && Objects.equals(jobLease, that.jobLease)
        && Objects.equals(iteration, that.iteration)
        && Objects.equals(role, that.role)
        && Objects.equals(commitStatus, that.commitStatus)
        && Objects.equals(producedAt, that.producedAt)
        && inputTokens == that.inputTokens
        && outputTokens == that.outputTokens
        && durationMs == that.durationMs
        && Objects.equals(content, that.content)
        && Objects.equals(toolCalls, that.toolCalls);
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
        + ", iteration="
        + iteration
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
        + ", durationMs="
        + durationMs
        + ", content="
        + content
        + ", toolCalls="
        + toolCalls
        + '}';
  }

  /**
   * A single content block in a history entry message. {@code text}, {@code documentReference}, and
   * {@code object} are mutually exclusive based on {@code contentType}; for {@code UNKNOWN} types
   * all available fields are populated as-is.
   */
  public record AgentHistoryContentValue(
      AgentHistoryContentType contentType,
      String text,
      DocumentReferenceEntity documentReference,
      Map<String, Object> object) {

    public static AgentHistoryContentValue text(final String text) {
      return new AgentHistoryContentValue(AgentHistoryContentType.TEXT, text, null, null);
    }

    public static AgentHistoryContentValue document(
        final DocumentReferenceEntity documentReference) {
      return new AgentHistoryContentValue(
          AgentHistoryContentType.DOCUMENT, null, documentReference, null);
    }

    public static AgentHistoryContentValue object(final Map<String, Object> object) {
      return new AgentHistoryContentValue(AgentHistoryContentType.OBJECT, null, null, object);
    }
  }

  /** A tool call embedded in a history entry. */
  public record AgentHistoryEmbeddedToolCallValue(
      String toolCallId, String toolName, String elementId, Map<String, Object> arguments) {}
}
