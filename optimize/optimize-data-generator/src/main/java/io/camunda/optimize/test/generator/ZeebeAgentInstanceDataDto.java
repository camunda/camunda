/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import java.util.List;
import java.util.Objects;

/**
 * Fixture data DTO for {@code AGENT_INSTANCE} Zeebe records.
 *
 * <p>This is a temporary fixture — it does not implement the (not-yet-existent) {@code
 * AgentInstanceRecordValue} interface from the Zeebe protocol. Replace with a proper DTO backed by
 * the real protocol interface once {@code ValueType.AGENT_INSTANCE} is introduced.
 *
 * <p>The nested {@link Definition}, {@link Limits}, and {@link Metrics} static classes produce the
 * correct JSON nesting that matches the spec's secondary-storage schema.
 */
public class ZeebeAgentInstanceDataDto {

  // ── Identity (set once at creation) ──────────────────────────────────────

  private long agentInstanceKey = -1L;
  private long elementInstanceKey = -1L;
  private String elementId = "";
  private long processInstanceKey = -1L;
  private long processDefinitionKey = -1L;
  private String bpmnProcessId = "";
  private int processDefinitionVersion = -1;
  private String versionTag = "";
  private String tenantId = ZEEBE_DEFAULT_TENANT_ID;

  // ── Definition (immutable after CREATED) ─────────────────────────────────

  private Definition definition = new Definition();

  // ── Limits (immutable after CREATED) ─────────────────────────────────────

  private Limits limits = new Limits();

  // ── Status ────────────────────────────────────────────────────────────────

  private AgentInstanceStatus status = AgentInstanceStatus.INITIALIZING;

  // ── Metrics (engine-aggregated running totals on UPDATED/COMPLETED) ───────

  private Metrics metrics = new Metrics();

  // ── Tools (replace semantics on each UPDATE) ─────────────────────────────

  private List<AgentTool> tools = List.of();

  public ZeebeAgentInstanceDataDto() {}

  // ── Identity getters/setters ──────────────────────────────────────────────

  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public void setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  // ── Definition getters/setters ────────────────────────────────────────────

  public Definition getDefinition() {
    return definition;
  }

  public void setDefinition(final Definition definition) {
    this.definition = definition;
  }

  // ── Limits getters/setters ────────────────────────────────────────────────

  public Limits getLimits() {
    return limits;
  }

  public void setLimits(final Limits limits) {
    this.limits = limits;
  }

  // ── Status getters/setters ────────────────────────────────────────────────

  public AgentInstanceStatus getStatus() {
    return status;
  }

  public void setStatus(final AgentInstanceStatus status) {
    this.status = status;
  }

  // ── Metrics getters/setters ───────────────────────────────────────────────

  public Metrics getMetrics() {
    return metrics;
  }

  public void setMetrics(final Metrics metrics) {
    this.metrics = metrics;
  }

  // ── Tools getters/setters ─────────────────────────────────────────────────

  public List<AgentTool> getTools() {
    return tools;
  }

  public void setTools(final List<AgentTool> tools) {
    this.tools = tools;
  }

  // ── equals / hashCode / toString ─────────────────────────────────────────

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeAgentInstanceDataDto that = (ZeebeAgentInstanceDataDto) o;
    return agentInstanceKey == that.agentInstanceKey
        && elementInstanceKey == that.elementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(definition, that.definition)
        && Objects.equals(limits, that.limits)
        && Objects.equals(status, that.status)
        && Objects.equals(metrics, that.metrics)
        && Objects.equals(tools, that.tools);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentInstanceKey,
        elementInstanceKey,
        elementId,
        processInstanceKey,
        processDefinitionKey,
        bpmnProcessId,
        processDefinitionVersion,
        versionTag,
        tenantId,
        definition,
        limits,
        status,
        metrics,
        tools);
  }

  @Override
  public String toString() {
    return "ZeebeAgentInstanceDataDto("
        + "agentInstanceKey="
        + agentInstanceKey
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", elementId='"
        + elementId
        + "', processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + "', processDefinitionVersion="
        + processDefinitionVersion
        + ", tenantId='"
        + tenantId
        + "', status="
        + status
        + ", definition="
        + definition
        + ", limits="
        + limits
        + ", metrics="
        + metrics
        + ", tools="
        + tools
        + ')';
  }

  // ── Nested types ──────────────────────────────────────────────────────────

  /**
   * Immutable-after-CREATED definition block: LLM model, provider, and system prompt.
   *
   * <p>Serializes as a nested {@code "definition"} object in the record JSON.
   */
  public static final class Definition {

    private String model = "";
    private String provider = "";
    private String systemPrompt = "";

    public Definition() {}

    public Definition(final String model, final String provider, final String systemPrompt) {
      this.model = model;
      this.provider = provider;
      this.systemPrompt = systemPrompt;
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

    public String getSystemPrompt() {
      return systemPrompt;
    }

    public void setSystemPrompt(final String systemPrompt) {
      this.systemPrompt = systemPrompt;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Definition that = (Definition) o;
      return Objects.equals(model, that.model)
          && Objects.equals(provider, that.provider)
          && Objects.equals(systemPrompt, that.systemPrompt);
    }

    @Override
    public int hashCode() {
      return Objects.hash(model, provider, systemPrompt);
    }

    @Override
    public String toString() {
      return "Definition(model='" + model + "', provider='" + provider + "')";
    }
  }

  /**
   * Immutable-after-CREATED limits block. A value of {@code -1} means no limit is configured.
   *
   * <p>Serializes as a nested {@code "limits"} object in the record JSON.
   */
  public static final class Limits {

    private long maxTokens = -1L;
    private int maxModelCalls = -1;
    private int maxToolCalls = -1;

    public Limits() {}

    public Limits(final long maxTokens, final int maxModelCalls, final int maxToolCalls) {
      this.maxTokens = maxTokens;
      this.maxModelCalls = maxModelCalls;
      this.maxToolCalls = maxToolCalls;
    }

    public long getMaxTokens() {
      return maxTokens;
    }

    public void setMaxTokens(final long maxTokens) {
      this.maxTokens = maxTokens;
    }

    public int getMaxModelCalls() {
      return maxModelCalls;
    }

    public void setMaxModelCalls(final int maxModelCalls) {
      this.maxModelCalls = maxModelCalls;
    }

    public int getMaxToolCalls() {
      return maxToolCalls;
    }

    public void setMaxToolCalls(final int maxToolCalls) {
      this.maxToolCalls = maxToolCalls;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Limits that = (Limits) o;
      return maxTokens == that.maxTokens
          && maxModelCalls == that.maxModelCalls
          && maxToolCalls == that.maxToolCalls;
    }

    @Override
    public int hashCode() {
      return Objects.hash(maxTokens, maxModelCalls, maxToolCalls);
    }

    @Override
    public String toString() {
      return "Limits(maxTokens="
          + maxTokens
          + ", maxModelCalls="
          + maxModelCalls
          + ", maxToolCalls="
          + maxToolCalls
          + ')';
    }
  }

  /**
   * Engine-aggregated running-totals block.
   *
   * <p>On UPDATED/COMPLETED events these are cumulative totals, not deltas. Serializes as a nested
   * {@code "metrics"} object in the record JSON.
   */
  public static final class Metrics {

    private long inputTokens = 0L;
    private long outputTokens = 0L;
    private int modelCalls = 0;
    private int toolCalls = 0;

    public Metrics() {}

    public Metrics(
        final long inputTokens,
        final long outputTokens,
        final int modelCalls,
        final int toolCalls) {
      this.inputTokens = inputTokens;
      this.outputTokens = outputTokens;
      this.modelCalls = modelCalls;
      this.toolCalls = toolCalls;
    }

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

    public int getModelCalls() {
      return modelCalls;
    }

    public void setModelCalls(final int modelCalls) {
      this.modelCalls = modelCalls;
    }

    public int getToolCalls() {
      return toolCalls;
    }

    public void setToolCalls(final int toolCalls) {
      this.toolCalls = toolCalls;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Metrics that = (Metrics) o;
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
      return "Metrics(inputTokens="
          + inputTokens
          + ", outputTokens="
          + outputTokens
          + ", modelCalls="
          + modelCalls
          + ", toolCalls="
          + toolCalls
          + ')';
    }
  }

  // ── Static field-name constants (for use in queries) ─────────────────────

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String agentInstanceKey = "agentInstanceKey";
    public static final String elementInstanceKey = "elementInstanceKey";
    public static final String elementId = "elementId";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String processDefinitionVersion = "processDefinitionVersion";
    public static final String versionTag = "versionTag";
    public static final String tenantId = "tenantId";
    public static final String status = "status";
    public static final String definition = "definition";
    public static final String limits = "limits";
    public static final String metrics = "metrics";
    public static final String tools = "tools";
  }
}
