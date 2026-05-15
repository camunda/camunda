/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.COMPLETION_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.ELEMENT_INSTANCE_KEYS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.INPUT_TOKENS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.LAST_UPDATED_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.MODEL_CALLS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.OUTPUT_TOKENS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.STATUS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.TOOLS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.TOOL_CALLS;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity.AgentInstanceToolValue;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentInstanceHandler
    implements ExportHandler<AgentInstanceEntity, AgentInstanceRecordValue> {

  private static final Set<AgentInstanceIntent> HANDLED_INTENTS =
      Set.of(
          AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);

  private final String indexName;

  public AgentInstanceHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.AGENT_INSTANCE;
  }

  @Override
  public Class<AgentInstanceEntity> getEntityType() {
    return AgentInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<AgentInstanceRecordValue> record) {
    final AgentInstanceIntent intent = (AgentInstanceIntent) record.getIntent();
    return HANDLED_INTENTS.contains(intent);
  }

  @Override
  public List<String> generateIds(final Record<AgentInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public AgentInstanceEntity createNewEntity(final String id) {
    return new AgentInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<AgentInstanceRecordValue> record, final AgentInstanceEntity entity) {
    final AgentInstanceRecordValue value = record.getValue();
    final AgentInstanceIntent intent = (AgentInstanceIntent) record.getIntent();
    final OffsetDateTime timestamp =
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));

    entity
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setElementId(value.getElementId())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        // `value.getRootProcessInstanceKey()` is not yet available in the record, so we set it to
        // -1 for now. Once it is available, this line should be updated to use the actual value.
        // tracked by: https://github.com/camunda/camunda/issues/53236
        .setRootProcessInstanceKey(-1)
        .setBpmnProcessId(value.getBpmnProcessId())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setProcessDefinitionVersion(value.getProcessDefinitionVersion())
        .setVersionTag(value.getVersionTag())
        .setTenantId(value.getTenantId())
        .setStatus(mapStatus(value.getStatus()))
        .setModel(value.getDefinition().getModel())
        .setProvider(value.getDefinition().getProvider())
        .setSystemPrompt(value.getDefinition().getSystemPrompt())
        .setMaxTokens(value.getLimits().getMaxTokens())
        .setMaxModelCalls(value.getLimits().getMaxModelCalls())
        .setMaxToolCalls(value.getLimits().getMaxToolCalls())
        .setInputTokens(value.getMetrics().getInputTokens())
        .setOutputTokens(value.getMetrics().getOutputTokens())
        .setModelCalls(value.getMetrics().getModelCalls())
        .setToolCalls(value.getMetrics().getToolCalls())
        .setTools(mapTools(value.getTools()))
        // once record will return `getElementInstanceKeys()` the line below should be adjusted
        .setElementInstanceKeys(List.of(value.getElementInstanceKey()))
        .setLastUpdatedDate(timestamp);

    if (intent == AgentInstanceIntent.CREATED) {
      entity.setCreationDate(timestamp);
    }
    if (intent == AgentInstanceIntent.COMPLETED) {
      entity.setCompletionDate(timestamp);
    }
  }

  @Override
  public void flush(final AgentInstanceEntity entity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(STATUS, entity.getStatus());
    updateFields.put(INPUT_TOKENS, entity.getInputTokens());
    updateFields.put(OUTPUT_TOKENS, entity.getOutputTokens());
    updateFields.put(MODEL_CALLS, entity.getModelCalls());
    updateFields.put(TOOL_CALLS, entity.getToolCalls());
    updateFields.put(TOOLS, entity.getTools());
    updateFields.put(ELEMENT_INSTANCE_KEYS, entity.getElementInstanceKeys());
    updateFields.put(LAST_UPDATED_DATE, entity.getLastUpdatedDate());
    if (entity.getCompletionDate() != null) {
      updateFields.put(COMPLETION_DATE, entity.getCompletionDate());
    }
    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private static AgentInstanceStatus mapStatus(
      final io.camunda.zeebe.protocol.record.value.AgentInstanceStatus protocolStatus) {
    return switch (protocolStatus) {
      case INITIALIZING -> AgentInstanceStatus.INITIALIZING;
      case IDLE -> AgentInstanceStatus.IDLE;
      case THINKING -> AgentInstanceStatus.THINKING;
      case TOOL_CALLING -> AgentInstanceStatus.TOOL_CALLING;
      case TOOL_DISCOVERY -> AgentInstanceStatus.TOOL_DISCOVERY;
      case COMPLETED -> AgentInstanceStatus.COMPLETED;
    };
  }

  private static List<AgentInstanceToolValue> mapTools(
      final List<? extends AgentInstanceRecordValue.AgentInstanceToolValue> protocolTools) {
    return protocolTools.stream()
        .map(t -> new AgentInstanceToolValue(t.getName(), t.getDescription(), t.getElementId()))
        .toList();
  }
}
