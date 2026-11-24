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
import io.camunda.optimize.service.db.writer.DeletedProcessDefinitionFilter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.AgentDefinitionDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

public class ZeebeAgentDefinitionImportService
    implements ImportService<ZeebeAgentDefinitionRecordDto> {

  // DELETED is filtered out by the exporter in Optimize mode; Optimize removes agents through its
  // own process definition cleanup, not through engine deletes.
  private static final Set<AgentDefinitionIntent> INTENTS_TO_IMPORT =
      Set.of(AgentDefinitionIntent.CREATED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeAgentDefinitionImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final AgentDefinitionWriter agentDefinitionWriter;
  private final ConfigurationService configurationService;
  private final int partitionId;
  private final DatabaseClient databaseClient;
  private final DeletedProcessDefinitionFilter deletedProcessDefinitionFilter;

  public ZeebeAgentDefinitionImportService(
      final ConfigurationService configurationService,
      final AgentDefinitionWriter agentDefinitionWriter,
      final int partitionId,
      final DatabaseClient databaseClient,
      final DeletedProcessDefinitionFilter deletedProcessDefinitionFilter) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.agentDefinitionWriter = agentDefinitionWriter;
    this.configurationService = configurationService;
    this.partitionId = partitionId;
    this.databaseClient = databaseClient;
    this.deletedProcessDefinitionFilter = deletedProcessDefinitionFilter;
  }

  @Override
  public void executeImport(
      final List<ZeebeAgentDefinitionRecordDto> pageOfAgentDefinitions,
      final Runnable importCompleteCallback) {
    LOG.trace("Importing agent definitions from zeebe records...");

    final boolean newDataIsAvailable = !pageOfAgentDefinitions.isEmpty();
    if (newDataIsAvailable) {
      // Skip the versions a user removed, so that a replay of their records does not add them
      // back. Optimize uses C7 naming: the engine's process definition key is its definition ID.
      final List<ZeebeAgentDefinitionRecordDto> recordsOfKeptDefinitions =
          deletedProcessDefinitionFilter.filterOutSuppressed(
              pageOfAgentDefinitions,
              zeebeRecord -> String.valueOf(zeebeRecord.getValue().getProcessDefinitionKey()));
      final List<AgentDefinitionDto> newOptimizeEntities =
          filterAndMapZeebeRecordsToOptimizeEntities(recordsOfKeptDefinitions);
      final DatabaseImportJob<AgentDefinitionDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(
      final DatabaseImportJob<AgentDefinitionDto> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  /**
   * Folds the page into one entity per agent and tenant, so the writer sends one upsert per agent
   * document rather than one per record of the same agent.
   */
  static List<AgentDefinitionDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeAgentDefinitionRecordDto> zeebeRecords) {
    final Map<String, List<ZeebeAgentDefinitionDataDto>> recordsByAgent = new LinkedHashMap<>();
    zeebeRecords.stream()
        .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
        .map(ZeebeAgentDefinitionRecordDto::getValue)
        .forEach(
            value ->
                recordsByAgent
                    .computeIfAbsent(
                        AgentIds.agentDefinitionDocumentId(
                            value.getTenantId(), value.getBpmnProcessId(), value.getElementId()),
                        id -> new ArrayList<>())
                    .add(value));
    final List<AgentDefinitionDto> optimizeDtos =
        recordsByAgent.values().stream()
            .map(ZeebeAgentDefinitionImportService::mapZeebeRecordsToOptimizeEntity)
            .toList();
    LOG.debug(
        "Processing {} fetched zeebe agent definition records into {} agent definitions.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private DatabaseImportJob<AgentDefinitionDto> createDatabaseImportJob(
      final List<AgentDefinitionDto> agentDefinitions, final Runnable importCompleteCallback) {
    final AgentDefinitionDatabaseImportJob agentDefinitionImportJob =
        new AgentDefinitionDatabaseImportJob(
            agentDefinitionWriter,
            configurationService,
            importCompleteCallback,
            databaseClient,
            partitionId);
    agentDefinitionImportJob.setEntitiesToImport(agentDefinitions);
    return agentDefinitionImportJob;
  }

  /**
   * Maps the agent definition records of one agent, possibly from several process versions, to its
   * Optimize document. Name and agent type come from the highest process version.
   *
   * <p><b>Terminology note:</b> Optimize uses C7 conventions, so the BPMN process ID lands in
   * {@code processDefinitionKey}. See {@code optimize/docs/adr/001-c7-naming-conventions.md}.
   */
  private static AgentDefinitionDto mapZeebeRecordsToOptimizeEntity(
      final List<ZeebeAgentDefinitionDataDto> versions) {
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
    dto.setProcessDefinitionVersions(
        versions.stream()
            .map(version -> String.valueOf(version.getProcessDefinitionVersion()))
            .distinct()
            .toList());
    return dto;
  }
}
