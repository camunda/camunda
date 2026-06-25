/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentHistoryDbModel implements Copyable<AgentHistoryDbModel> {

  private static final Logger LOG = LoggerFactory.getLogger(AgentHistoryDbModel.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private long agentHistoryKey;
  private long agentInstanceKey;
  private long elementInstanceKey;
  private long processInstanceKey;
  private long rootProcessInstanceKey;
  private String processDefinitionId;
  private long processDefinitionKey;
  private String tenantId;
  private int partitionId;
  private long jobKey;
  private String jobLease;
  private Integer iteration;
  private AgentInstanceHistoryRole role;
  private AgentInstanceHistoryCommitStatus commitStatus;
  private OffsetDateTime producedAt;
  private long inputTokens;
  private long outputTokens;
  private long durationMs;
  private String content;
  private List<ContentItem> contentItems;
  private String toolCalls;
  private List<ToolCall> toolCallValues;

  public AgentHistoryDbModel() {}

  @Override
  public AgentHistoryDbModel copy(
      final Function<ObjectBuilder<AgentHistoryDbModel>, ObjectBuilder<AgentHistoryDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public ObjectBuilder<AgentHistoryDbModel> toBuilder() {
    return new Builder()
        .agentHistoryKey(agentHistoryKey)
        .agentInstanceKey(agentInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .processInstanceKey(processInstanceKey)
        .rootProcessInstanceKey(rootProcessInstanceKey)
        .processDefinitionId(processDefinitionId)
        .processDefinitionKey(processDefinitionKey)
        .tenantId(tenantId)
        .partitionId(partitionId)
        .jobKey(jobKey)
        .jobLease(jobLease)
        .iteration(iteration)
        .role(role)
        .commitStatus(commitStatus)
        .producedAt(producedAt)
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .durationMs(durationMs)
        // route through the getter so DB-hydrated models (only `content` JSON populated)
        // are lazily deserialized and the structured form is preserved on the copy
        .contentItems(contentItems())
        // route through the getter so DB-hydrated models (only `toolCalls` JSON populated)
        // are lazily deserialized and the structured form is preserved on the copy
        .toolCallValues(toolCallValues());
  }

  public long agentHistoryKey() {
    return agentHistoryKey;
  }

  public void agentHistoryKey(final long agentHistoryKey) {
    this.agentHistoryKey = agentHistoryKey;
  }

  public long agentInstanceKey() {
    return agentInstanceKey;
  }

  public void agentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
  }

  public long elementInstanceKey() {
    return elementInstanceKey;
  }

  public void elementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
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

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public void processDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void processDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
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

  public long jobKey() {
    return jobKey;
  }

  public void jobKey(final long jobKey) {
    this.jobKey = jobKey;
  }

  public String jobLease() {
    return jobLease;
  }

  public void jobLease(final String jobLease) {
    this.jobLease = jobLease;
  }

  public void truncateJobLease(final int sizeLimit, final Integer byteLimit) {
    if (TruncateUtil.shouldTruncate(jobLease, sizeLimit, byteLimit)) {
      jobLease = TruncateUtil.truncateValue(jobLease, sizeLimit, byteLimit);
    }
  }

  public Integer iteration() {
    return iteration;
  }

  public void iteration(final Integer iteration) {
    this.iteration = iteration;
  }

  public AgentInstanceHistoryRole role() {
    return role;
  }

  public void role(final AgentInstanceHistoryRole role) {
    this.role = role;
  }

  public AgentInstanceHistoryCommitStatus commitStatus() {
    return commitStatus;
  }

  public void commitStatus(final AgentInstanceHistoryCommitStatus commitStatus) {
    this.commitStatus = commitStatus;
  }

  public OffsetDateTime producedAt() {
    return producedAt;
  }

  public void producedAt(final OffsetDateTime producedAt) {
    this.producedAt = producedAt;
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

  public long durationMs() {
    return durationMs;
  }

  public void durationMs(final long durationMs) {
    this.durationMs = durationMs;
  }

  public String content() {
    return content;
  }

  /**
   * Setter used by MyBatis when hydrating this model from the DB. Stores the raw JSON and
   * invalidates the cached structured form so the next {@link #contentItems()} call re-derives from
   * the new JSON (otherwise readers see stale data).
   */
  public void content(final String content) {
    this.content = content;
    contentItems = null;
  }

  /**
   * Returns the structured content list. If only the JSON form (set via {@link #content(String)},
   * e.g. when the model is hydrated from the DB) is present, the JSON is deserialized lazily and
   * cached on the model.
   */
  public List<ContentItem> contentItems() {
    if (contentItems == null && content != null && !content.isEmpty()) {
      contentItems = deserializeContentItems(content);
    }
    return contentItems;
  }

  /**
   * Sets the structured content list and derives the JSON form from it. The JSON is what MyBatis
   * writes to the {@code AGENT_HISTORY.CONTENT} CLOB column.
   */
  public void contentItems(final List<ContentItem> contentItems) {
    this.contentItems = contentItems;
    content = serializeContentItems(contentItems);
  }

  private static List<ContentItem> deserializeContentItems(final String json) {
    if (json == null || json.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to deserialize agent history content items", e);
      return Collections.emptyList();
    }
  }

  private static String serializeContentItems(final List<ContentItem> items) {
    if (items == null || items.isEmpty()) {
      return null;
    }

    try {
      return MAPPER.writeValueAsString(items);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize agent history content items", e);
      return null;
    }
  }

  public String toolCalls() {
    return toolCalls;
  }

  /**
   * Setter used by MyBatis when hydrating this model from the DB. Stores the raw JSON and
   * invalidates the cached structured form so the next {@link #toolCallValues()} call re-derives
   * from the new JSON (otherwise readers see stale data).
   */
  public void toolCalls(final String toolCalls) {
    this.toolCalls = toolCalls;
    toolCallValues = null;
  }

  /**
   * Returns the structured tool-call list. If only the JSON form (set via {@link
   * #toolCalls(String)}, e.g. when the model is hydrated from the DB) is present, the JSON is
   * deserialized lazily and cached on the model.
   */
  public List<ToolCall> toolCallValues() {
    if (toolCallValues == null && toolCalls != null && !toolCalls.isEmpty()) {
      toolCallValues = deserializeToolCallValues(toolCalls);
    }
    return toolCallValues;
  }

  /**
   * Sets the structured tool-call list and derives the JSON form from it. The JSON is what MyBatis
   * writes to the {@code AGENT_HISTORY.TOOL_CALLS} CLOB column.
   */
  public void toolCallValues(final List<ToolCall> toolCallValues) {
    this.toolCallValues = toolCallValues;
    toolCalls = serializeToolCallValues(toolCallValues);
  }

  private static List<ToolCall> deserializeToolCallValues(final String json) {
    if (json == null || json.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to deserialize agent history tool calls", e);
      return Collections.emptyList();
    }
  }

  private static String serializeToolCallValues(final List<ToolCall> toolCallValues) {
    if (toolCallValues == null || toolCallValues.isEmpty()) {
      return null;
    }

    try {
      return MAPPER.writeValueAsString(toolCallValues);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize agent history tool calls", e);
      return null;
    }
  }

  public static class Builder implements ObjectBuilder<AgentHistoryDbModel> {

    private long agentHistoryKey;
    private long agentInstanceKey;
    private long elementInstanceKey;
    private long processInstanceKey;
    private long rootProcessInstanceKey;
    private String processDefinitionId;
    private long processDefinitionKey;
    private String tenantId;
    private int partitionId;
    private long jobKey;
    private String jobLease;
    private Integer iteration;
    private AgentInstanceHistoryRole role;
    private AgentInstanceHistoryCommitStatus commitStatus;
    private OffsetDateTime producedAt;
    private long inputTokens;
    private long outputTokens;
    private long durationMs;
    private List<ContentItem> contentItems;
    private List<ToolCall> toolCallValues;

    public Builder agentHistoryKey(final long agentHistoryKey) {
      this.agentHistoryKey = agentHistoryKey;
      return this;
    }

    public Builder agentInstanceKey(final long agentInstanceKey) {
      this.agentInstanceKey = agentInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(final long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
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

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder processDefinitionKey(final long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
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

    public Builder jobKey(final long jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder jobLease(final String jobLease) {
      this.jobLease = jobLease;
      return this;
    }

    public Builder iteration(final Integer iteration) {
      this.iteration = iteration;
      return this;
    }

    public Builder role(final AgentInstanceHistoryRole role) {
      this.role = role;
      return this;
    }

    public Builder commitStatus(final AgentInstanceHistoryCommitStatus commitStatus) {
      this.commitStatus = commitStatus;
      return this;
    }

    public Builder producedAt(final OffsetDateTime producedAt) {
      this.producedAt = producedAt;
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

    public Builder durationMs(final long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    public Builder contentItems(final List<ContentItem> contentItems) {
      this.contentItems = contentItems;
      return this;
    }

    public Builder toolCallValues(final List<ToolCall> toolCallValues) {
      this.toolCallValues = toolCallValues;
      return this;
    }

    @Override
    public AgentHistoryDbModel build() {
      final var result = new AgentHistoryDbModel();
      result.agentHistoryKey(agentHistoryKey);
      result.agentInstanceKey(agentInstanceKey);
      result.elementInstanceKey(elementInstanceKey);
      result.processInstanceKey(processInstanceKey);
      result.rootProcessInstanceKey(rootProcessInstanceKey);
      result.processDefinitionId(processDefinitionId);
      result.processDefinitionKey(processDefinitionKey);
      result.tenantId(tenantId);
      result.partitionId(partitionId);
      result.jobKey(jobKey);
      result.jobLease(jobLease);
      result.iteration(iteration);
      result.role(role);
      result.commitStatus(commitStatus);
      result.producedAt(producedAt);
      result.inputTokens(inputTokens);
      result.outputTokens(outputTokens);
      result.durationMs(durationMs);
      result.contentItems(contentItems);
      result.toolCallValues(toolCallValues);
      return result;
    }
  }
}
