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
import org.camunda.optimize.service.db.writer.TenantWriter;
import org.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.importing.job.TenantDatabaseImportJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class TenantImportService implements ImportService<TenantEngineDto> {
  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final EngineContext engineContext;
  private final TenantWriter tenantWriter;

  public TenantImportService(final ConfigurationService configurationService,
                             final EngineContext engineContext,
                             final TenantWriter tenantWriter) {
    this.databaseImportJobExecutor = new DatabaseImportJobExecutor(
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
      addDatabaseImportJobToQueue(createDatabaseImportJob(newOptimizeEntities, importCompleteCallback));
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(final DatabaseImportJob databaseimportJob) {
    databaseImportJobExecutor.executeImportJob(databaseimportJob);
  }

  private List<TenantDto> mapEngineEntitiesToOptimizeEntities(final List<TenantEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private DatabaseImportJob<TenantDto> createDatabaseImportJob(final List<TenantDto> tenantDtos,
                                                                    final Runnable importCompleteCallback) {
    final TenantDatabaseImportJob importJob = new TenantDatabaseImportJob(
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
