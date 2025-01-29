/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.zeebe.definition.ZeebeDecisionDefinitionDataDto;
import io.camunda.optimize.dto.zeebe.definition.ZeebeDecisionDefinitionRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.DecisionDefinitionDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeDecisionDefinitionImportService
    implements ImportService<ZeebeDecisionDefinitionRecordDto> {

  private static final Set<DecisionRequirementsIntent> INTENTS_TO_IMPORT =
      Set.of(DecisionRequirementsIntent.CREATED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeDecisionDefinitionImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final DecisionDefinitionWriter decisionDefinitionWriter;
  private final ConfigurationService configurationService;
  private final int partitionId;
  private final DatabaseClient databaseClient;

  public ZeebeDecisionDefinitionImportService(
      final ConfigurationService configurationService,
      final DecisionDefinitionWriter decisionDefinitionWriter,
      final int partitionId,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
    this.partitionId = partitionId;
    this.configurationService = configurationService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeDecisionDefinitionRecordDto> pageOfDecisionDefinitions,
      final Runnable importCompleteCallback) {
    LOG.trace("Importing decision definitions from zeebe records...");

    final boolean newDataIsAvailable = !pageOfDecisionDefinitions.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionDefinitionOptimizeDto> newOptimizeEntities =
          filterAndMapZeebeRecordsToOptimizeEntities(pageOfDecisionDefinitions);
      final DatabaseImportJob<DecisionDefinitionOptimizeDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(
      final DatabaseImportJob<DecisionDefinitionOptimizeDto> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  private List<DecisionDefinitionOptimizeDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeDecisionDefinitionRecordDto> zeebeRecords) {
    final List<DecisionDefinitionOptimizeDto> optimizeDtos =
        zeebeRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .map(this::mapZeebeRecordsToOptimizeEntities)
            .collect(Collectors.toList());
    LOG.debug(
        "Processing {} fetched zeebe decision definition records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private DatabaseImportJob<DecisionDefinitionOptimizeDto> createDatabaseImportJob(
      final List<DecisionDefinitionOptimizeDto> decisionDefinitions,
      final Runnable importCompleteCallback) {
    final DecisionDefinitionDatabaseImportJob decDefImportJob =
        new DecisionDefinitionDatabaseImportJob(
            decisionDefinitionWriter, importCompleteCallback, databaseClient);
    decDefImportJob.setEntitiesToImport(decisionDefinitions);
    return decDefImportJob;
  }

  private DecisionDefinitionOptimizeDto mapZeebeRecordsToOptimizeEntities(
      final ZeebeDecisionDefinitionRecordDto zeebeDecisionDefinitionRecord) {
    final ZeebeDecisionDefinitionDataDto recordData = zeebeDecisionDefinitionRecord.getValue();
    final String dmn = new String(recordData.getResource(), StandardCharsets.UTF_8);
    return DecisionDefinitionOptimizeDto.builder()
        .id(String.valueOf(recordData.getDecisionRequirementsKey()))
        .key(String.valueOf(recordData.getDecisionRequirementsId()))
        .version(String.valueOf(recordData.getDecisionRequirementsVersion()))
        .versionTag(null)
        .name(recordData.getDecisionRequirementsName())
        .dmn10Xml(dmn)
        .dataSource(
            new ZeebeDataSourceDto(
                configurationService.getConfiguredZeebe().getName(), partitionId))
        .tenantId(recordData.getTenantId())
        .deleted(false)
        .build();
  }
}
