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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Tool;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import io.camunda.search.entities.ContentItem;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record AgentHistoryDbModel(
    long agentHistoryKey,
    long agentInstanceKey,
    long elementInstanceKey,
    long processInstanceKey,
    long rootProcessInstanceKey,
    String processDefinitionId,
    long processDefinitionKey,
    String tenantId,
    int partitionId,
    long jobKey,
    String jobLease,
    int loopIteration,
    AgentInstanceHistoryRole role,
    AgentInstanceHistoryCommitStatus commitStatus,
    OffsetDateTime producedAt,
    Long inputTokens,
    Long outputTokens,
    Long reasoningTokenCount,
    Long cacheCreationTokenCount,
    Long cacheReadTokenCount,
    Long durationMs,
    String content,
    String toolCalls,
    String historyItemId,
    String tools,
    String model,
    String provider,
    Long maxTokens,
    Integer maxModelCalls,
    Integer maxToolCalls,
    String systemPrompt)
    implements Copyable<AgentHistoryDbModel> {

  private static final Logger LOG = LoggerFactory.getLogger(AgentHistoryDbModel.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Override
  public AgentHistoryDbModel copy(
      final Function<ObjectBuilder<AgentHistoryDbModel>, ObjectBuilder<AgentHistoryDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public AgentHistoryDbModel truncate(final int sizeLimit, final Integer byteLimit) {
    final var truncatedJobLease = TruncateUtil.truncateValue(jobLease, sizeLimit, byteLimit);
    final var truncatedHistoryItemId =
        TruncateUtil.truncateValue(historyItemId, sizeLimit, byteLimit);
    final var truncatedModel = TruncateUtil.truncateValue(model, sizeLimit, byteLimit);
    final var truncatedProvider = TruncateUtil.truncateValue(provider, sizeLimit, byteLimit);
    if (Objects.equals(truncatedJobLease, jobLease)
        && Objects.equals(truncatedHistoryItemId, historyItemId)
        && Objects.equals(truncatedModel, model)
        && Objects.equals(truncatedProvider, provider)) {
      return this;
    }

    return new AgentHistoryDbModel(
        agentHistoryKey,
        agentInstanceKey,
        elementInstanceKey,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionId,
        processDefinitionKey,
        tenantId,
        partitionId,
        jobKey,
        truncatedJobLease,
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
        truncatedHistoryItemId,
        tools,
        truncatedModel,
        truncatedProvider,
        maxTokens,
        maxModelCalls,
        maxToolCalls,
        systemPrompt);
  }

  /**
   * Returns the structured content list, deserializing the JSON form on every call. Returns null,
   * not an empty list, when no JSON is stored.
   */
  public List<ContentItem> contentItems() {
    if (content == null || content.isEmpty()) {
      return null;
    }
    return deserializeContentItems(content);
  }

  /**
   * Returns the structured tool-call list, deserializing the JSON form on every call. Returns null,
   * not an empty list, when no JSON is stored.
   */
  public List<ToolCall> toolCallValues() {
    if (toolCalls == null || toolCalls.isEmpty()) {
      return null;
    }
    return deserializeToolCallValues(toolCalls);
  }

  /**
   * Returns the structured tool list of a CONFIGURATION item, deserializing the JSON form on every
   * call. Returns an empty list, not null, when this item didn't touch the tool list.
   */
  public List<Tool> toolValues() {
    if (tools == null || tools.isEmpty()) {
      return List.of();
    }
    return deserializeTools(tools);
  }

  private static List<ContentItem> deserializeContentItems(final String json) {
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

  private static List<ToolCall> deserializeToolCallValues(final String json) {
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

  private static List<Tool> deserializeTools(final String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to deserialize agent history tools", e);
      return Collections.emptyList();
    }
  }

  private static String serializeTools(final List<Tool> toolValues) {
    if (toolValues == null || toolValues.isEmpty()) {
      return null;
    }

    try {
      return MAPPER.writeValueAsString(toolValues);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize agent history tools", e);
      return null;
    }
  }

  public Builder toBuilder() {
    return new Builder(content, toolCalls, tools, systemPrompt)
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
        .loopIteration(loopIteration)
        .role(role)
        .commitStatus(commitStatus)
        .producedAt(producedAt)
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .reasoningTokenCount(reasoningTokenCount)
        .cacheCreationTokenCount(cacheCreationTokenCount)
        .cacheReadTokenCount(cacheReadTokenCount)
        .durationMs(durationMs)
        .historyItemId(historyItemId)
        .model(model)
        .provider(provider)
        .maxTokens(maxTokens)
        .maxModelCalls(maxModelCalls)
        .maxToolCalls(maxToolCalls);
  }

  /**
   * Returns the structured system-prompt content list of a CONFIGURATION item, deserializing the
   * JSON form on every call. Returns an empty list, not null, when this item didn't touch the
   * system prompt.
   */
  public List<ContentItem> systemPromptItems() {
    if (systemPrompt == null || systemPrompt.isEmpty()) {
      return List.of();
    }
    return deserializeContentItems(systemPrompt);
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
    private int loopIteration;
    private AgentInstanceHistoryRole role;
    private AgentInstanceHistoryCommitStatus commitStatus;
    private OffsetDateTime producedAt;
    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokenCount;
    private Long cacheCreationTokenCount;
    private Long cacheReadTokenCount;
    private Long durationMs;
    private String content;
    private String toolCalls;
    private String historyItemId;
    private String tools;
    private String model;
    private String provider;
    private Long maxTokens;
    private Integer maxModelCalls;
    private Integer maxToolCalls;
    private String systemPrompt;

    public Builder() {}

    // Seeds the raw serialized column values for toBuilder()/copy(), so a copy that never
    // touches contentItems()/toolCallValues()/toolValues()/systemPromptItems() carries the
    // original JSON through unparsed instead of round-tripping it through Jackson.
    // Package-private, not a public setter: a second public setter aliasing the same fields as
    // contentItems(List)/toolCallValues(List)/toolValues(List)/systemPromptItems(List) would let
    // callers silently drop a write (e.g. builder.content(x).contentItems(y) loses x).
    Builder(
        final String content,
        final String toolCalls,
        final String tools,
        final String systemPrompt) {
      this.content = content;
      this.toolCalls = toolCalls;
      this.tools = tools;
      this.systemPrompt = systemPrompt;
    }

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

    public Builder loopIteration(final int loopIteration) {
      this.loopIteration = loopIteration;
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

    public Builder inputTokens(final Long inputTokens) {
      this.inputTokens = inputTokens;
      return this;
    }

    public Builder outputTokens(final Long outputTokens) {
      this.outputTokens = outputTokens;
      return this;
    }

    public Builder reasoningTokenCount(final Long reasoningTokenCount) {
      this.reasoningTokenCount = reasoningTokenCount;
      return this;
    }

    public Builder cacheCreationTokenCount(final Long cacheCreationTokenCount) {
      this.cacheCreationTokenCount = cacheCreationTokenCount;
      return this;
    }

    public Builder cacheReadTokenCount(final Long cacheReadTokenCount) {
      this.cacheReadTokenCount = cacheReadTokenCount;
      return this;
    }

    public Builder durationMs(final Long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    public Builder contentItems(final List<ContentItem> contentItems) {
      content = serializeContentItems(contentItems);
      return this;
    }

    public Builder toolCallValues(final List<ToolCall> toolCallValues) {
      toolCalls = serializeToolCallValues(toolCallValues);
      return this;
    }

    public Builder historyItemId(final String historyItemId) {
      this.historyItemId = historyItemId;
      return this;
    }

    public Builder toolValues(final List<Tool> toolValues) {
      tools = serializeTools(toolValues);
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

    public Builder maxTokens(final Long maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    public Builder maxModelCalls(final Integer maxModelCalls) {
      this.maxModelCalls = maxModelCalls;
      return this;
    }

    public Builder maxToolCalls(final Integer maxToolCalls) {
      this.maxToolCalls = maxToolCalls;
      return this;
    }

    public Builder systemPromptItems(final List<ContentItem> systemPromptItems) {
      systemPrompt = serializeContentItems(systemPromptItems);
      return this;
    }

    @Override
    public AgentHistoryDbModel build() {
      return new AgentHistoryDbModel(
          agentHistoryKey,
          agentInstanceKey,
          elementInstanceKey,
          processInstanceKey,
          rootProcessInstanceKey,
          processDefinitionId,
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
          maxTokens,
          maxModelCalls,
          maxToolCalls,
          systemPrompt);
    }
  }
}
