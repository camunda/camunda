/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity;
import io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import java.util.List;
import java.util.Set;

/** Exports {@code AgentDefinition:CREATED} records. */
public class AgentDefinitionHandler
    implements MainIndexExporterHandler<AgentDefinitionEntity, AgentDefinitionRecordValue> {

  private static final Set<AgentDefinitionIntent> HANDLED_INTENTS =
      Set.of(AgentDefinitionIntent.CREATED);

  private final String indexName;

  public AgentDefinitionHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.AGENT_DEFINITION;
  }

  @Override
  public Class<AgentDefinitionEntity> getEntityType() {
    return AgentDefinitionEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<AgentDefinitionRecordValue> record) {
    final AgentDefinitionIntent intent = (AgentDefinitionIntent) record.getIntent();
    return HANDLED_INTENTS.contains(intent);
  }

  @Override
  public List<String> generateIds(final Record<AgentDefinitionRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getAgentDefinitionKey()));
  }

  @Override
  public AgentDefinitionEntity createNewEntity(final String id) {
    return new AgentDefinitionEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<AgentDefinitionRecordValue> record, final AgentDefinitionEntity entity) {
    final AgentDefinitionRecordValue value = record.getValue();
    entity
        .setId(String.valueOf(value.getAgentDefinitionKey()))
        .setKey(value.getAgentDefinitionKey())
        .setAgentType(mapAgentType(value.getAgentType()))
        .setName(value.getName())
        .setElementId(value.getElementId())
        .setBpmnProcessId(value.getBpmnProcessId())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setProcessDefinitionVersion(value.getProcessDefinitionVersion())
        .setProcessDefinitionVersionTag(
            ExporterUtil.emptyToNull(value.getProcessDefinitionVersionTag()))
        .setTenantId(value.getTenantId());
  }

  @Override
  public void flush(
      final TargetIndex index,
      final AgentDefinitionEntity entity,
      final BatchRequest batchRequest) {
    batchRequest.add(index, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  /**
   * Maps the protocol {@code AgentDefinitionType} to the secondary-storage {@link
   * AgentDefinitionType}.
   */
  private static AgentDefinitionType mapAgentType(
      final io.camunda.zeebe.protocol.record.value.AgentDefinitionType protocolAgentType) {
    return switch (protocolAgentType) {
      case AI_AGENT_SUB_PROCESS -> AgentDefinitionType.AI_AGENT_SUB_PROCESS;
      case AI_AGENT_TASK -> AgentDefinitionType.AI_AGENT_TASK;
      case EXTERNAL_AGENT -> AgentDefinitionType.EXTERNAL_AGENT;
      case UNSPECIFIED ->
          throw new IllegalStateException(
              "Received unexpected AgentDefinitionType.UNSPECIFIED; the broker must never emit "
                  + "this value. If a new AgentDefinitionType was intentionally added, extend "
                  + AgentDefinitionType.class.getName()
                  + " and this mapping accordingly.");
    };
  }
}
