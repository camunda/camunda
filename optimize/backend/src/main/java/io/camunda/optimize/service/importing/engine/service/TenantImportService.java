/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service;

import io.camunda.optimize.dto.engine.TenantEngineDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.TenantWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.job.TenantDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantImportService implements ImportService<TenantEngineDto> {

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final EngineContext engineContext;
  private final TenantWriter tenantWriter;
  private final DatabaseClient databaseClient;

  public TenantImportService(
      final ConfigurationService configurationService,
      final EngineContext engineContext,
      final TenantWriter tenantWriter,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.engineContext = engineContext;
    this.tenantWriter = tenantWriter;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<TenantEngineDto> pageOfEngineEntities, final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");
    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<TenantDto> newOptimizeEntities =
          mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      addDatabaseImportJobToQueue(
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback));
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(final DatabaseImportJob databaseimportJob) {
    databaseImportJobExecutor.executeImportJob(databaseimportJob);
  }

  private List<TenantDto> mapEngineEntitiesToOptimizeEntities(
      final List<TenantEngineDto> engineEntities) {
    return engineEntities.stream()
        .map(this::mapEngineEntityToOptimizeEntity)
        .collect(Collectors.toList());
  }

  private DatabaseImportJob<TenantDto> createDatabaseImportJob(
      final List<TenantDto> tenantDtos, final Runnable importCompleteCallback) {
    final TenantDatabaseImportJob importJob =
        new TenantDatabaseImportJob(tenantWriter, importCompleteCallback, databaseClient);
    importJob.setEntitiesToImport(tenantDtos);
    return importJob;
  }

  private TenantDto mapEngineEntityToOptimizeEntity(final TenantEngineDto engineEntity) {
    return new TenantDto(
        engineEntity.getId(), engineEntity.getName(), engineContext.getEngineAlias());
  }
}
