/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentIds;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionDataDto;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.AgentDefinitionDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Imports agent definition records into the flat agent-definition index. The engine mints one agent
 * definition per process version; they are folded into one document per logical agent (tenant, BPMN
 * process ID, element ID).
 */
public class ZeebeAgentDefinitionImportService
    implements ImportService<ZeebeAgentDefinitionRecordDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeAgentDefinitionImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final AgentDefinitionWriter agentDefinitionWriter;
  private final ConfigurationService configurationService;
  private final int partitionId;
  private final DatabaseClient databaseClient;

  public ZeebeAgentDefinitionImportService(
      final ConfigurationService configurationService,
      final AgentDefinitionWriter agentDefinitionWriter,
      final int partitionId,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.agentDefinitionWriter = agentDefinitionWriter;
    this.configurationService = configurationService;
    this.partitionId = partitionId;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeAgentDefinitionRecordDto> pageOfRecords,
      final Runnable importCompleteCallback) {
    LOG.trace("Importing agent definitions from zeebe records...");
    if (!pageOfRecords.isEmpty()) {
      final List<AgentDefinitionDto> agentDefinitions = mapToAgentDefinitions(pageOfRecords);
      final DatabaseImportJob<AgentDefinitionDto> importJob =
          new AgentDefinitionDatabaseImportJob(
              agentDefinitionWriter,
              configurationService,
              importCompleteCallback,
              databaseClient,
              partitionId);
      importJob.setEntitiesToImport(agentDefinitions);
      databaseImportJobExecutor.executeImportJob(importJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  static List<AgentDefinitionDto> mapToAgentDefinitions(
      final List<ZeebeAgentDefinitionRecordDto> records) {
    final Map<String, List<ZeebeAgentDefinitionDataDto>> byAgent = new LinkedHashMap<>();
    records.stream()
        .filter(record -> record.getIntent() == AgentDefinitionIntent.CREATED)
        .map(ZeebeAgentDefinitionRecordDto::getValue)
        .forEach(
            value ->
                byAgent
                    .computeIfAbsent(
                        AgentIds.agentDefinitionDocumentId(
                            value.getTenantId(), value.getBpmnProcessId(), value.getElementId()),
                        id -> new ArrayList<>())
                    .add(value));
    final List<AgentDefinitionDto> definitions =
        byAgent.values().stream()
            .map(ZeebeAgentDefinitionImportService::toAgentDefinition)
            .toList();
    LOG.debug(
        "Processing {} fetched zeebe agent definition records into {} agent definitions.",
        records.size(),
        definitions.size());
    return definitions;
  }

  /**
   * Maps the agent definitions of one logical agent to its document.
   *
   * <p><b>Terminology note:</b> Optimize uses C7 conventions, so the record's unique {@code
   * agentDefinitionKey} lands in {@code agentDefinitionIds} and the BPMN process ID in {@code
   * processDefinitionKey}. See {@code optimize/docs/adr/001-c7-naming-conventions.md}.
   */
  private static AgentDefinitionDto toAgentDefinition(
      final Collection<ZeebeAgentDefinitionDataDto> versions) {
    final ZeebeAgentDefinitionDataDto latest =
        versions.stream()
            .max(Comparator.comparingInt(ZeebeAgentDefinitionDataDto::getProcessDefinitionVersion))
            .orElseThrow();
    final AgentDefinitionDto dto = new AgentDefinitionDto();
    dto.setAgentId(AgentIds.agentId(latest.getBpmnProcessId(), latest.getElementId()));
    dto.setTenantId(latest.getTenantId());
    dto.setProcessDefinitionKey(latest.getBpmnProcessId());
    dto.setFlowNodeId(latest.getElementId());
    dto.setName(latest.getName());
    dto.setAgentType(latest.getAgentType() == null ? null : latest.getAgentType().name());
    dto.setAgentDefinitionIds(
        versions.stream().map(v -> String.valueOf(v.getAgentDefinitionKey())).distinct().toList());
    dto.setProcessDefinitionVersions(
        versions.stream()
            .map(v -> String.valueOf(v.getProcessDefinitionVersion()))
            .distinct()
            .toList());
    return dto;
  }
}
