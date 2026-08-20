/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel.AgentDefinitionDbModelBuilder;
import io.camunda.db.rdbms.write.service.AgentDefinitionWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.ExportUtil;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import java.util.Set;

/**
 * Exports {@code AgentDefinition:CREATED} records. The engine replicates {@code
 * AgentDefinition:CREATED} with the same key to every partition, the same way {@code
 * Process:CREATED} and {@code Decision:CREATED} are, so this handler is only registered on {@code
 * RdbmsExporterWrapper#PROCESS_DEFINITION_PARTITION} to avoid every partition's exporter inserting
 * the same {@code AGENT_DEFINITION_KEY} row into the shared RDBMS table.
 */
public class AgentDefinitionExportHandler
    implements RdbmsExportHandler<AgentDefinitionRecordValue> {

  private static final Set<AgentDefinitionIntent> EXPORTABLE_INTENTS =
      Set.of(AgentDefinitionIntent.CREATED);

  private final AgentDefinitionWriter writer;

  public AgentDefinitionExportHandler(final AgentDefinitionWriter writer) {
    this.writer = writer;
  }

  @Override
  public boolean canExport(final Record<AgentDefinitionRecordValue> record) {
    return record.getValueType() == ValueType.AGENT_DEFINITION
        && record.getIntent() instanceof final AgentDefinitionIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<AgentDefinitionRecordValue> record) {
    writer.create(map(record.getValue()));
  }

  private AgentDefinitionDbModel map(final AgentDefinitionRecordValue value) {
    return new AgentDefinitionDbModelBuilder()
        .agentDefinitionKey(value.getAgentDefinitionKey())
        .agentType(mapAgentType(value.getAgentType()))
        .name(value.getName())
        .elementId(value.getElementId())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processDefinitionVersion(value.getProcessDefinitionVersion())
        .processDefinitionVersionTag(ExportUtil.emptyToNull(value.getProcessDefinitionVersionTag()))
        .tenantId(value.getTenantId())
        .build();
  }

  /** Maps the protocol {@code AgentDefinitionType} to the search-side {@link AgentType}. */
  private static AgentType mapAgentType(
      final io.camunda.zeebe.protocol.record.value.AgentDefinitionType protocolAgentType) {
    return switch (protocolAgentType) {
      case AI_AGENT_SUB_PROCESS -> AgentType.AI_AGENT_SUB_PROCESS;
      case AI_AGENT_TASK -> AgentType.AI_AGENT_TASK;
      case EXTERNAL_AGENT -> AgentType.EXTERNAL_AGENT;
      case UNSPECIFIED ->
          throw new IllegalStateException(
              "Received unexpected AgentDefinitionType.UNSPECIFIED; the broker must never emit "
                  + "this value. If a new AgentDefinitionType was intentionally added, extend "
                  + AgentType.class.getName()
                  + " and this mapping accordingly.");
    };
  }
}
