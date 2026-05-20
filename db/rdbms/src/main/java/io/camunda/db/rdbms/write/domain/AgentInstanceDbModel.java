/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

public class AgentInstanceDbModel implements Copyable<AgentInstanceDbModel> {

  private long agentInstanceKey;
  private String elementId;
  private long processInstanceKey;
  private long rootProcessInstanceKey;
  private String bpmnProcessId;
  private long processDefinitionKey;
  private int processDefinitionVersion;
  private String versionTag;
  private String tenantId;
  private int partitionId;
  private AgentInstanceStatus status;
  private String model;
  private String provider;
  private String systemPrompt;
  private long maxTokens;
  private int maxModelCalls;
  private int maxToolCalls;
  private long inputTokens;
  private long outputTokens;
  private int modelCalls;
  private int toolCalls;
  private String tools;
  private OffsetDateTime creationDate;
  private OffsetDateTime lastUpdatedDate;
  private OffsetDateTime completionDate;
  private List<Long> elementInstanceKeys;

  public AgentInstanceDbModel() {}

  @Override
  public AgentInstanceDbModel copy(
      final Function<ObjectBuilder<AgentInstanceDbModel>, ObjectBuilder<AgentInstanceDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public ObjectBuilder<AgentInstanceDbModel> toBuilder() {
    return new Builder()
        .agentInstanceKey(agentInstanceKey)
        .elementId(elementId)
        .processInstanceKey(processInstanceKey)
        .rootProcessInstanceKey(rootProcessInstanceKey)
        .bpmnProcessId(bpmnProcessId)
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionVersion(processDefinitionVersion)
        .versionTag(versionTag)
        .tenantId(tenantId)
        .partitionId(partitionId)
        .status(status)
        .model(model)
        .provider(provider)
        .systemPrompt(systemPrompt)
        .maxTokens(maxTokens)
        .maxModelCalls(maxModelCalls)
        .maxToolCalls(maxToolCalls)
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .modelCalls(modelCalls)
        .toolCalls(toolCalls)
        .tools(tools)
        .creationDate(creationDate)
        .lastUpdatedDate(lastUpdatedDate)
        .completionDate(completionDate)
        .elementInstanceKeys(elementInstanceKeys);
  }

  public long agentInstanceKey() {
    return agentInstanceKey;
  }

  public void agentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
  }

  public String elementId() {
    return elementId;
  }

  public void elementId(final String elementId) {
    this.elementId = elementId;
  }

  public long processInstanceKey() {
    return processInstanceKey;
  }

  public void processInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public long rootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public void rootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
  }

  public String bpmnProcessId() {
    return bpmnProcessId;
  }

  public void bpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void processDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public int processDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void processDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String versionTag() {
    return versionTag;
  }

  public void versionTag(final String versionTag) {
    this.versionTag = versionTag;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int partitionId() {
    return partitionId;
  }

  public void partitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public AgentInstanceStatus status() {
    return status;
  }

  public void status(final AgentInstanceStatus status) {
    this.status = status;
  }

  public String model() {
    return model;
  }

  public void model(final String model) {
    this.model = model;
  }

  public String provider() {
    return provider;
  }

  public void provider(final String provider) {
    this.provider = provider;
  }

  public String systemPrompt() {
    return systemPrompt;
  }

  public void systemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public long maxTokens() {
    return maxTokens;
  }

  public void maxTokens(final long maxTokens) {
    this.maxTokens = maxTokens;
  }

  public int maxModelCalls() {
    return maxModelCalls;
  }

  public void maxModelCalls(final int maxModelCalls) {
    this.maxModelCalls = maxModelCalls;
  }

  public int maxToolCalls() {
    return maxToolCalls;
  }

  public void maxToolCalls(final int maxToolCalls) {
    this.maxToolCalls = maxToolCalls;
  }

  public long inputTokens() {
    return inputTokens;
  }

  public void inputTokens(final long inputTokens) {
    this.inputTokens = inputTokens;
  }

  public long outputTokens() {
    return outputTokens;
  }

  public void outputTokens(final long outputTokens) {
    this.outputTokens = outputTokens;
  }

  public int modelCalls() {
    return modelCalls;
  }

  public void modelCalls(final int modelCalls) {
    this.modelCalls = modelCalls;
  }

  public int toolCalls() {
    return toolCalls;
  }

  public void toolCalls(final int toolCalls) {
    this.toolCalls = toolCalls;
  }

  public String tools() {
    return tools;
  }

  public void tools(final String tools) {
    this.tools = tools;
  }

  public OffsetDateTime creationDate() {
    return creationDate;
  }

  public void creationDate(final OffsetDateTime creationDate) {
    this.creationDate = creationDate;
  }

  public OffsetDateTime lastUpdatedDate() {
    return lastUpdatedDate;
  }

  public void lastUpdatedDate(final OffsetDateTime lastUpdatedDate) {
    this.lastUpdatedDate = lastUpdatedDate;
  }

  public OffsetDateTime completionDate() {
    return completionDate;
  }

  public void completionDate(final OffsetDateTime completionDate) {
    this.completionDate = completionDate;
  }

  public List<Long> elementInstanceKeys() {
    return elementInstanceKeys;
  }

  public void elementInstanceKeys(final List<Long> elementInstanceKeys) {
    this.elementInstanceKeys = elementInstanceKeys;
  }

  public static class Builder implements ObjectBuilder<AgentInstanceDbModel> {

    private long agentInstanceKey;
    private String elementId;
    private long processInstanceKey;
    private long rootProcessInstanceKey;
    private String bpmnProcessId;
    private long processDefinitionKey;
    private int processDefinitionVersion;
    private String versionTag;
    private String tenantId;
    private int partitionId;
    private AgentInstanceStatus status;
    private String model;
    private String provider;
    private String systemPrompt;
    private long maxTokens;
    private int maxModelCalls;
    private int maxToolCalls;
    private long inputTokens;
    private long outputTokens;
    private int modelCalls;
    private int toolCalls;
    private String tools;
    private OffsetDateTime creationDate;
    private OffsetDateTime lastUpdatedDate;
    private OffsetDateTime completionDate;
    private List<Long> elementInstanceKeys;

    public Builder agentInstanceKey(final long agentInstanceKey) {
      this.agentInstanceKey = agentInstanceKey;
      return this;
    }

    public Builder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public Builder processInstanceKey(final long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder bpmnProcessId(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public Builder processDefinitionKey(final long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionVersion(final int processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    public Builder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder status(final AgentInstanceStatus status) {
      this.status = status;
      return this;
    }

    public Builder model(final String model) {
      this.model = model;
      return this;
    }

    public Builder provider(final String provider) {
      this.provider = provider;
      return this;
    }

    public Builder systemPrompt(final String systemPrompt) {
      this.systemPrompt = systemPrompt;
      return this;
    }

    public Builder maxTokens(final long maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    public Builder maxModelCalls(final int maxModelCalls) {
      this.maxModelCalls = maxModelCalls;
      return this;
    }

    public Builder maxToolCalls(final int maxToolCalls) {
      this.maxToolCalls = maxToolCalls;
      return this;
    }

    public Builder inputTokens(final long inputTokens) {
      this.inputTokens = inputTokens;
      return this;
    }

    public Builder outputTokens(final long outputTokens) {
      this.outputTokens = outputTokens;
      return this;
    }

    public Builder modelCalls(final int modelCalls) {
      this.modelCalls = modelCalls;
      return this;
    }

    public Builder toolCalls(final int toolCalls) {
      this.toolCalls = toolCalls;
      return this;
    }

    public Builder tools(final String tools) {
      this.tools = tools;
      return this;
    }

    public Builder creationDate(final OffsetDateTime creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    public Builder lastUpdatedDate(final OffsetDateTime lastUpdatedDate) {
      this.lastUpdatedDate = lastUpdatedDate;
      return this;
    }

    public Builder completionDate(final OffsetDateTime completionDate) {
      this.completionDate = completionDate;
      return this;
    }

    public Builder elementInstanceKeys(final List<Long> elementInstanceKeys) {
      this.elementInstanceKeys = elementInstanceKeys;
      return this;
    }

    @Override
    public AgentInstanceDbModel build() {
      final var result = new AgentInstanceDbModel();
      result.agentInstanceKey(agentInstanceKey);
      result.elementId(elementId);
      result.processInstanceKey(processInstanceKey);
      result.rootProcessInstanceKey(rootProcessInstanceKey);
      result.bpmnProcessId(bpmnProcessId);
      result.processDefinitionKey(processDefinitionKey);
      result.processDefinitionVersion(processDefinitionVersion);
      result.versionTag(versionTag);
      result.tenantId(tenantId);
      result.partitionId(partitionId);
      result.status(status);
      result.model(model);
      result.provider(provider);
      result.systemPrompt(systemPrompt);
      result.maxTokens(maxTokens);
      result.maxModelCalls(maxModelCalls);
      result.maxToolCalls(maxToolCalls);
      result.inputTokens(inputTokens);
      result.outputTokens(outputTokens);
      result.modelCalls(modelCalls);
      result.toolCalls(toolCalls);
      result.tools(tools);
      result.creationDate(creationDate);
      result.lastUpdatedDate(lastUpdatedDate);
      result.completionDate(completionDate);
      result.elementInstanceKeys(elementInstanceKeys);
      return result;
    }
  }

  /**
   * Stored as the enum constant name in the AGENT_INSTANCE.STATUS column via MyBatis' default enum
   * type handler. UNKNOWN is the fallback for unrecognised protocol statuses (mirrors the
   * entity-side enum in webapps-schema).
   */
  public enum AgentInstanceStatus {
    UNKNOWN,
    INITIALIZING,
    TOOL_DISCOVERY,
    IDLE,
    THINKING,
    TOOL_CALLING,
    COMPLETED
  }
}
