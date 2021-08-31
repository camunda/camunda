/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ExternalVariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ExternalVariableUpdateImportService implements ImportService<ExternalProcessVariableDto> {

  public static final long DEFAULT_VERSION = 1000L;

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final ProcessVariableUpdateWriter variableWriter;
  private final ConfigurationService configurationService;

  public ExternalVariableUpdateImportService(final ConfigurationService configurationService,
                                             final ProcessVariableUpdateWriter variableWriter) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
  }

  @Override
  public void executeImport(final List<ExternalProcessVariableDto> pageOfExternalEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing external variable entities...");

    boolean newDataIsAvailable = !pageOfExternalEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessVariableDto> newOptimizeEntities = mapExternalEntitiesToOptimizeEntities(pageOfExternalEntities);
      ElasticsearchImportJob<ProcessVariableDto> elasticsearchImportJob = createElasticsearchImportJob(
        newOptimizeEntities,
        importCompleteCallback
      );
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob<?> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessVariableDto> mapExternalEntitiesToOptimizeEntities(final List<ExternalProcessVariableDto> externalEntities) {
    return externalEntities.stream().map(this::mapEngineEntityToOptimizeEntity).collect(Collectors.toList());
  }

  private ProcessVariableDto mapEngineEntityToOptimizeEntity(final ExternalProcessVariableDto externalEntity) {
    return new ProcessVariableDto(
      externalEntity.getVariableId(),
      externalEntity.getVariableName(),
      externalEntity.getVariableType().getId(),
      externalEntity.getVariableValue(),
      OffsetDateTime.ofInstant(Instant.ofEpochMilli(externalEntity.getIngestionTimestamp()), ZoneId.systemDefault()),
      null,
      externalEntity.getProcessDefinitionKey(),
      null,
      externalEntity.getProcessInstanceId(),
      // defaulting to the same version as there is no versioning for external variables
      DEFAULT_VERSION,
      null,
      null
    );
  }

  private ElasticsearchImportJob<ProcessVariableDto> createElasticsearchImportJob(final List<ProcessVariableDto> processVariables,
                                                                                  final Runnable callback) {
    final ExternalVariableUpdateElasticsearchImportJob importJob = new ExternalVariableUpdateElasticsearchImportJob(
      variableWriter, configurationService, callback
    );
    importJob.setEntitiesToImport(processVariables);
    return importJob;
  }

}
