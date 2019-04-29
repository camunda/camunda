/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.TenantElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.TenantWriter;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class TenantImportService implements ImportService<TenantEngineDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final TenantWriter tenantWriter;

  @Override
  public void executeImport(List<TenantEngineDto> pageOfEngineEntities) {
    log.trace("Importing entities from engine...");
    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<TenantDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      addElasticsearchImportJobToQueue(createElasticsearchImportJob(newOptimizeEntities));
    }
  }

  @Override
  public void executeImport(final List<TenantEngineDto> pageOfEngineEntities, final Runnable callback) {
    executeImport(pageOfEngineEntities);
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<TenantDto> mapEngineEntitiesToOptimizeEntities(final List<TenantEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<TenantDto> createElasticsearchImportJob(final List<TenantDto> tenantDtos) {
    final TenantElasticsearchImportJob importJob = new TenantElasticsearchImportJob(tenantWriter);
    importJob.setEntitiesToImport(tenantDtos);
    return importJob;
  }

  private TenantDto mapEngineEntityToOptimizeEntity(TenantEngineDto engineEntity) {
    return new TenantDto(
      engineEntity.getId(), engineEntity.getName(), engineContext.getEngineAlias()
    );
  }

}
