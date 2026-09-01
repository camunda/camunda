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
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.entities.ContentItem;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record AgentInstanceDbModel(
    long agentInstanceKey,
    long agentDefinitionKey,
    String elementId,
    long processInstanceKey,
    long rootProcessInstanceKey,
    String processDefinitionId,
    long processDefinitionKey,
    int processDefinitionVersion,
    String processDefinitionVersionTag,
    String tenantId,
    int partitionId,
    AgentInstanceStatus status,
    String model,
    String provider,
    String systemPrompt,
    long maxTokens,
    int maxModelCalls,
    int maxToolCalls,
    long inputTokens,
    long outputTokens,
    int modelCalls,
    int toolCalls,
    String tools,
    OffsetDateTime creationDate,
    OffsetDateTime lastUpdatedDate,
    OffsetDateTime completionDate,
    List<Long> elementInstanceKeys)
    implements Copyable<AgentInstanceDbModel> {

  private static final Logger LOG = LoggerFactory.getLogger(AgentInstanceDbModel.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  public AgentInstanceDbModel {
    // Must stay mutable: MyBatis appends to this via <collection> after construction.
    elementInstanceKeys = Objects.requireNonNullElse(elementInstanceKeys, new ArrayList<>());
  }

  // Matches searchResultMap's <constructor>, which omits elementInstanceKeys -- populated
  // separately via the sibling <collection> element.
  public AgentInstanceDbModel(
      final long agentInstanceKey,
      final long agentDefinitionKey,
      final String elementId,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final String processDefinitionId,
      final long processDefinitionKey,
      final int processDefinitionVersion,
      final String processDefinitionVersionTag,
      final String tenantId,
      final int partitionId,
      final AgentInstanceStatus status,
      final String model,
      final String provider,
      final String systemPrompt,
      final long maxTokens,
      final int maxModelCalls,
      final int maxToolCalls,
      final long inputTokens,
      final long outputTokens,
      final int modelCalls,
      final int toolCalls,
      final String tools,
      final OffsetDateTime creationDate,
      final OffsetDateTime lastUpdatedDate,
      final OffsetDateTime completionDate) {
    this(
        agentInstanceKey,
        agentDefinitionKey,
        elementId,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionId,
        processDefinitionKey,
        processDefinitionVersion,
        processDefinitionVersionTag,
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
        creationDate,
        lastUpdatedDate,
        completionDate,
        null);
  }

  @Override
  public AgentInstanceDbModel copy(
      final Function<ObjectBuilder<AgentInstanceDbModel>, ObjectBuilder<AgentInstanceDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public AgentInstanceDbModel truncateDefinitionFields(
      final int sizeLimit, final Integer byteLimit) {
    final var truncatedModel = TruncateUtil.truncateValue(model, sizeLimit, byteLimit);
    final var truncatedProvider = TruncateUtil.truncateValue(provider, sizeLimit, byteLimit);
    if (Objects.equals(truncatedModel, model) && Objects.equals(truncatedProvider, provider)) {
      return this;
    }

    return new AgentInstanceDbModel(
        agentInstanceKey,
        agentDefinitionKey,
        elementId,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionId,
        processDefinitionKey,
        processDefinitionVersion,
        processDefinitionVersionTag,
        tenantId,
        partitionId,
        status,
        truncatedModel,
        truncatedProvider,
        systemPrompt,
        maxTokens,
        maxModelCalls,
        maxToolCalls,
        inputTokens,
        outputTokens,
        modelCalls,
        toolCalls,
        tools,
        creationDate,
        lastUpdatedDate,
        completionDate,
        elementInstanceKeys);
  }

  /**
   * Returns the structured tool list, deserializing the JSON form on every call. Returns null, not
   * an empty list, when no JSON is stored -- covers both the never-set and the explicit-empty-list
   * case (an empty list also serializes to a null {@code tools}), as well as the Oracle
   * empty-string-treated-as-null edge case. Unlike the pre-record class, this implementation cannot
   * distinguish "never set" from "explicitly set to an empty list", since both collapse to the same
   * null string; no current caller depends on that distinction.
   */
  public List<AgentInstanceToolDbValue> toolValues() {
    if (tools == null || tools.isEmpty()) {
      return null;
    }
    return deserializeTools(tools);
  }

  /**
   * Returns the structured system-prompt content list, deserializing the JSON form on every call.
   * Returns an empty list, not null, when no JSON is stored.
   */
  public List<ContentItem> systemPromptItems() {
    if (systemPrompt == null || systemPrompt.isEmpty()) {
      return List.of();
    }
    return deserializeContentItems(systemPrompt);
  }

  private static List<AgentInstanceToolDbValue> deserializeTools(final String tools) {
    try {
      return MAPPER.readValue(tools, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to deserialize agent instance tools", e);
      return Collections.emptyList();
    }
  }

  private static String serializeTools(final List<AgentInstanceToolDbValue> toolValues) {
    if (toolValues == null || toolValues.isEmpty()) {
      return null;
    }

    try {
      return MAPPER.writeValueAsString(toolValues);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize agent instance tools", e);
      return null;
    }
  }

  private static List<ContentItem> deserializeContentItems(final String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to deserialize agent instance system prompt", e);
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
      LOG.error("Failed to serialize agent instance system prompt", e);
      return null;
    }
  }

  public Builder toBuilder() {
    return new Builder(tools)
        .agentInstanceKey(agentInstanceKey)
        .agentDefinitionKey(agentDefinitionKey)
        .elementId(elementId)
        .processInstanceKey(processInstanceKey)
        .rootProcessInstanceKey(rootProcessInstanceKey)
        .processDefinitionId(processDefinitionId)
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionVersion(processDefinitionVersion)
        .processDefinitionVersionTag(processDefinitionVersionTag)
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
        .creationDate(creationDate)
        .lastUpdatedDate(lastUpdatedDate)
        .completionDate(completionDate)
        .elementInstanceKeys(elementInstanceKeys);
  }

  public static class Builder implements ObjectBuilder<AgentInstanceDbModel> {

    private long agentInstanceKey;
    private long agentDefinitionKey;
    private String elementId;
    private long processInstanceKey;
    private long rootProcessInstanceKey;
    private String processDefinitionId;
    private long processDefinitionKey;
    private int processDefinitionVersion;
    private String processDefinitionVersionTag;
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

    public Builder() {}

    // Seeds the raw serialized column value for toBuilder()/copy(), so a copy that never touches
    // toolValues() carries the original JSON through unparsed instead of round-tripping it
    // through Jackson. Package-private, not a public setter: a second public setter aliasing the
    // same field as toolValues(List) would let callers silently drop one write (e.g.
    // builder.tools(x).toolValues(y) loses x).
    Builder(final String tools) {
      this.tools = tools;
    }

    public Builder agentInstanceKey(final long agentInstanceKey) {
      this.agentInstanceKey = agentInstanceKey;
      return this;
    }

    public Builder agentDefinitionKey(final long agentDefinitionKey) {
      this.agentDefinitionKey = agentDefinitionKey;
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

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
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

    public Builder processDefinitionVersionTag(final String processDefinitionVersionTag) {
      this.processDefinitionVersionTag = processDefinitionVersionTag;
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

    public Builder truncateDefinitionFields(final int sizeLimit, final Integer byteLimit) {
      model = TruncateUtil.truncateValue(model, sizeLimit, byteLimit);
      provider = TruncateUtil.truncateValue(provider, sizeLimit, byteLimit);
      return this;
    }

    public Builder systemPrompt(final String systemPrompt) {
      this.systemPrompt = systemPrompt;
      return this;
    }

    public Builder systemPromptItems(final List<ContentItem> systemPromptItems) {
      systemPrompt = serializeContentItems(systemPromptItems);
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

    public Builder toolValues(final List<AgentInstanceToolDbValue> toolValues) {
      tools = serializeTools(toolValues);
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
      return new AgentInstanceDbModel(
          agentInstanceKey,
          agentDefinitionKey,
          elementId,
          processInstanceKey,
          rootProcessInstanceKey,
          processDefinitionId,
          processDefinitionKey,
          processDefinitionVersion,
          processDefinitionVersionTag,
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
          creationDate,
          lastUpdatedDate,
          completionDate,
          elementInstanceKeys);
    }
  }

  /**
   * Structured input for {@link Builder#toolValues(List)}. Serialised by the model into the JSON
   * string stored in the AGENT_INSTANCE.TOOLS CLOB column.
   */
  public record AgentInstanceToolDbValue(String name, String description, String elementId) {}
}
