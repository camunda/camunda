/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionDataDto;
import io.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.ProcessDefinitionDatabaseImportJob;
import io.camunda.optimize.service.util.BpmnModelUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeProcessDefinitionImportService
    implements ImportService<ZeebeProcessDefinitionRecordDto> {

  private static final Set<ProcessIntent> INTENTS_TO_IMPORT = Set.of(ProcessIntent.CREATED);
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ZeebeProcessDefinitionImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ConfigurationService configurationService;
  private final int partitionId;
  private final DatabaseClient databaseClient;

  public ZeebeProcessDefinitionImportService(
      final ConfigurationService configurationService,
      final ProcessDefinitionWriter processDefinitionWriter,
      final int partitionId,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.processDefinitionWriter = processDefinitionWriter;
    this.partitionId = partitionId;
    this.configurationService = configurationService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeProcessDefinitionRecordDto> pageOfProcessDefinitions,
      final Runnable importCompleteCallback) {
    log.trace("Importing process definitions from zeebe records...");

    final boolean newDataIsAvailable = !pageOfProcessDefinitions.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessDefinitionOptimizeDto> newOptimizeEntities =
          filterAndMapZeebeRecordsToOptimizeEntities(pageOfProcessDefinitions);
      final DatabaseImportJob<ProcessDefinitionOptimizeDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(
      final DatabaseImportJob<ProcessDefinitionOptimizeDto> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  private List<ProcessDefinitionOptimizeDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeProcessDefinitionRecordDto> zeebeRecords) {
    final List<ProcessDefinitionOptimizeDto> optimizeDtos =
        zeebeRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .map(this::mapZeebeRecordsToOptimizeEntities)
            .collect(Collectors.toList());
    log.debug(
        "Processing {} fetched zeebe process definition records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private DatabaseImportJob<ProcessDefinitionOptimizeDto> createDatabaseImportJob(
      final List<ProcessDefinitionOptimizeDto> processDefinitions,
      final Runnable importCompleteCallback) {
    final ProcessDefinitionDatabaseImportJob procDefImportJob =
        new ProcessDefinitionDatabaseImportJob(
            processDefinitionWriter, importCompleteCallback, databaseClient);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapZeebeRecordsToOptimizeEntities(
      final ZeebeProcessDefinitionRecordDto zeebeProcessDefinitionRecord) {
    final ZeebeProcessDefinitionDataDto recordData = zeebeProcessDefinitionRecord.getValue();
    final String bpmn = new String(recordData.getResource(), StandardCharsets.UTF_8);
    return ProcessDefinitionOptimizeDto.builder()
        .id(String.valueOf(recordData.getProcessDefinitionKey()))
        .key(String.valueOf(recordData.getBpmnProcessId()))
        .version(String.valueOf(recordData.getVersion()))
        .versionTag(null)
        .name(
            BpmnModelUtil.extractProcessDefinitionName(
                    String.valueOf(recordData.getBpmnProcessId()), bpmn)
                .orElse(recordData.getBpmnProcessId()))
        .bpmn20Xml(bpmn)
        .dataSource(
            new ZeebeDataSourceDto(
                configurationService.getConfiguredZeebe().getName(), partitionId))
        .tenantId(recordData.getTenantId())
        .deleted(false)
        .flowNodeData(BpmnModelUtil.extractFlowNodeData(bpmn))
        .userTaskNames(BpmnModelUtil.extractUserTaskNames(bpmn))
        .build();
  }
}
