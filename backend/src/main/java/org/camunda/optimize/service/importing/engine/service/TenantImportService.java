/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.TenantElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.TenantWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class TenantImportService implements ImportService<TenantEngineDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final TenantWriter tenantWriter;

  public TenantImportService(final ConfigurationService configurationService,
                             final EngineContext engineContext,
                             final TenantWriter tenantWriter) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.tenantWriter = tenantWriter;
  }

  @Override
  public void executeImport(final List<TenantEngineDto> pageOfEngineEntities, final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");
    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<TenantDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      addElasticsearchImportJobToQueue(createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback));
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<TenantDto> mapEngineEntitiesToOptimizeEntities(final List<TenantEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<TenantDto> createElasticsearchImportJob(final List<TenantDto> tenantDtos,
                                                                         final Runnable importCompleteCallback) {
    final TenantElasticsearchImportJob importJob = new TenantElasticsearchImportJob(
      tenantWriter,
      importCompleteCallback
    );
    importJob.setEntitiesToImport(tenantDtos);
    return importJob;
  }

  private TenantDto mapEngineEntityToOptimizeEntity(TenantEngineDto engineEntity) {
    return new TenantDto(
      engineEntity.getId(), engineEntity.getName(), engineContext.getEngineAlias()
    );
  }

}
