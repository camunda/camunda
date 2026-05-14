/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.agentinstance;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Secondary-storage entity for AGENT_INSTANCE records. All nested sub-objects from the protocol
 * (definition, limits, metrics) are flattened to top-level fields to avoid nested object mapping
 * complexity in ES/OS and keep the RDBMS model flat.
 */
public final class AgentInstanceEntity
    implements ExporterEntity<AgentInstanceEntity>,
        PartitionedEntity<AgentInstanceEntity>,
        TenantOwned {

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long key;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String elementId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long processInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String bpmnProcessId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long processDefinitionKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int processDefinitionVersion;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String versionTag;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int partitionId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private AgentInstanceStatus status;

  // Definition fields — flattened from AgentInstanceDefinitionValue
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String model;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String provider;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String systemPrompt;

  // Limits fields — flattened from AgentInstanceLimitsValue
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long maxTokens;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int maxModelCalls;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int maxToolCalls;

  // Metrics fields — flattened from AgentInstanceMetricsValue
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long inputTokens;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private long outputTokens;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int modelCalls;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private int toolCalls;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<AgentInstanceToolValue> tools;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private List<Long> elementInstanceKeys;

  // Handler-computed dates — not present in AgentInstanceRecordValue
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private OffsetDateTime creationDate;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private OffsetDateTime lastUpdatedDate;

  /** Null until the agent instance reaches the COMPLETED state. */
  @SinceVersion(value = "8.10.0", requireDefault = false)
  private OffsetDateTime completionDate;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public AgentInstanceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public AgentInstanceEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public AgentInstanceEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public AgentInstanceEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public AgentInstanceEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public AgentInstanceEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public AgentInstanceEntity setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public AgentInstanceEntity setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public AgentInstanceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public AgentInstanceEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public AgentInstanceStatus getStatus() {
    return status;
  }

  public AgentInstanceEntity setStatus(final AgentInstanceStatus status) {
    this.status = status;
    return this;
  }

  public String getModel() {
    return model;
  }

  public AgentInstanceEntity setModel(final String model) {
    this.model = model;
    return this;
  }

  public String getProvider() {
    return provider;
  }

  public AgentInstanceEntity setProvider(final String provider) {
    this.provider = provider;
    return this;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public AgentInstanceEntity setSystemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
    return this;
  }

  public long getMaxTokens() {
    return maxTokens;
  }

  public AgentInstanceEntity setMaxTokens(final long maxTokens) {
    this.maxTokens = maxTokens;
    return this;
  }

  public int getMaxModelCalls() {
    return maxModelCalls;
  }

  public AgentInstanceEntity setMaxModelCalls(final int maxModelCalls) {
    this.maxModelCalls = maxModelCalls;
    return this;
  }

  public int getMaxToolCalls() {
    return maxToolCalls;
  }

  public AgentInstanceEntity setMaxToolCalls(final int maxToolCalls) {
    this.maxToolCalls = maxToolCalls;
    return this;
  }

  public long getInputTokens() {
    return inputTokens;
  }

  public AgentInstanceEntity setInputTokens(final long inputTokens) {
    this.inputTokens = inputTokens;
    return this;
  }

  public long getOutputTokens() {
    return outputTokens;
  }

  public AgentInstanceEntity setOutputTokens(final long outputTokens) {
    this.outputTokens = outputTokens;
    return this;
  }

  public int getModelCalls() {
    return modelCalls;
  }

  public AgentInstanceEntity setModelCalls(final int modelCalls) {
    this.modelCalls = modelCalls;
    return this;
  }

  public int getToolCalls() {
    return toolCalls;
  }

  public AgentInstanceEntity setToolCalls(final int toolCalls) {
    this.toolCalls = toolCalls;
    return this;
  }

  public List<AgentInstanceToolValue> getTools() {
    return tools;
  }

  public AgentInstanceEntity setTools(final List<AgentInstanceToolValue> tools) {
    this.tools = tools;
    return this;
  }

  public List<Long> getElementInstanceKeys() {
    return elementInstanceKeys;
  }

  public AgentInstanceEntity setElementInstanceKeys(final List<Long> elementInstanceKeys) {
    this.elementInstanceKeys = elementInstanceKeys;
    return this;
  }

  public OffsetDateTime getCreationDate() {
    return creationDate;
  }

  public AgentInstanceEntity setCreationDate(final OffsetDateTime creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public OffsetDateTime getLastUpdatedDate() {
    return lastUpdatedDate;
  }

  public AgentInstanceEntity setLastUpdatedDate(final OffsetDateTime lastUpdatedDate) {
    this.lastUpdatedDate = lastUpdatedDate;
    return this;
  }

  public OffsetDateTime getCompletionDate() {
    return completionDate;
  }

  public AgentInstanceEntity setCompletionDate(final OffsetDateTime completionDate) {
    this.completionDate = completionDate;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        elementId,
        processInstanceKey,
        bpmnProcessId,
        processDefinitionKey,
        processDefinitionVersion,
        versionTag,
        tenantId,
        partitionId,
        status,
        model,
        provider,
        systemPrompt,
        maxTokens,
        maxModelCalls,
        maxToolCalls,
        inputTokens,
        outputTokens,
        modelCalls,
        toolCalls,
        tools,
        elementInstanceKeys,
        creationDate,
        lastUpdatedDate,
        completionDate);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (AgentInstanceEntity) obj;
    return Objects.equals(id, that.id)
        && key == that.key
        && Objects.equals(elementId, that.elementId)
        && processInstanceKey == that.processInstanceKey
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(tenantId, that.tenantId)
        && partitionId == that.partitionId
        && Objects.equals(status, that.status)
        && Objects.equals(model, that.model)
        && Objects.equals(provider, that.provider)
        && Objects.equals(systemPrompt, that.systemPrompt)
        && maxTokens == that.maxTokens
        && maxModelCalls == that.maxModelCalls
        && maxToolCalls == that.maxToolCalls
        && inputTokens == that.inputTokens
        && outputTokens == that.outputTokens
        && modelCalls == that.modelCalls
        && toolCalls == that.toolCalls
        && Objects.equals(tools, that.tools)
        && Objects.equals(elementInstanceKeys, that.elementInstanceKeys)
        && Objects.equals(creationDate, that.creationDate)
        && Objects.equals(lastUpdatedDate, that.lastUpdatedDate)
        && Objects.equals(completionDate, that.completionDate);
  }

  @Override
  public String toString() {
    return "AgentInstanceEntity{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", elementId='"
        + elementId
        + '\''
        + ", processInstanceKey="
        + processInstanceKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", versionTag='"
        + versionTag
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", partitionId="
        + partitionId
        + ", status="
        + status
        + ", model='"
        + model
        + '\''
        + ", provider='"
        + provider
        + '\''
        + ", systemPrompt='"
        + systemPrompt
        + '\''
        + ", maxTokens="
        + maxTokens
        + ", maxModelCalls="
        + maxModelCalls
        + ", maxToolCalls="
        + maxToolCalls
        + ", inputTokens="
        + inputTokens
        + ", outputTokens="
        + outputTokens
        + ", modelCalls="
        + modelCalls
        + ", toolCalls="
        + toolCalls
        + ", tools="
        + tools
        + ", elementInstanceKeys="
        + elementInstanceKeys
        + ", creationDate="
        + creationDate
        + ", lastUpdatedDate="
        + lastUpdatedDate
        + ", completionDate="
        + completionDate
        + '}';
  }

  public record AgentInstanceToolValue(String name, String description, String elementId) {}
}
