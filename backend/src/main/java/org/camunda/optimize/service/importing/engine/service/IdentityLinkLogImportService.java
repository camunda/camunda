/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.dto.engine.HistoricIdentityLinkLogDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.IdentityLinkLogElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.usertask.IdentityLinkLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class IdentityLinkLogImportService implements ImportService<HistoricIdentityLinkLogDto> {
  private static final Logger logger = LoggerFactory.getLogger(IdentityLinkLogImportService.class);

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final IdentityLinkLogWriter identityLinkLogWriter;

  public IdentityLinkLogImportService(final IdentityLinkLogWriter identityLinkLogWriter,
                                      final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                      final EngineContext engineContext) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.identityLinkLogWriter = identityLinkLogWriter;
  }

  @Override
  public void executeImport(final List<HistoricIdentityLinkLogDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    logger.trace("Importing identity link logs from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<IdentityLinkLogEntryDto> newOptimizeEntities =
        mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final ElasticsearchImportJob<IdentityLinkLogEntryDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<IdentityLinkLogEntryDto> mapEngineEntitiesToOptimizeEntities(final List<HistoricIdentityLinkLogDto> engineEntities) {
    return engineEntities.stream()
      .filter(instance -> instance.getProcessInstanceId() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<IdentityLinkLogEntryDto> createElasticsearchImportJob(final List<IdentityLinkLogEntryDto> identityLinkLogs,
                                                                                       Runnable callback) {
    final IdentityLinkLogElasticsearchImportJob importJob = new IdentityLinkLogElasticsearchImportJob(
      identityLinkLogWriter,
      callback
    );
    importJob.setEntitiesToImport(identityLinkLogs);
    return importJob;
  }

  private IdentityLinkLogEntryDto mapEngineEntityToOptimizeEntity(final HistoricIdentityLinkLogDto engineEntity) {
    return new IdentityLinkLogEntryDto(
      engineEntity.getId(),
      engineEntity.getProcessInstanceId(),
      engineContext.getEngineAlias(),
      engineEntity.getType(),
      engineEntity.getUserId(),
      engineEntity.getGroupId(),
      engineEntity.getTaskId(),
      engineEntity.getOperationType(),
      engineEntity.getAssignerId(),
      engineEntity.getTime()
    );
  }

}
