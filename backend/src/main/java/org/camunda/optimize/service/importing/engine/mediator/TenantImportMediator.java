/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.service.importing.BackoffImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.TenantFetcher;
import org.camunda.optimize.service.importing.engine.handler.TenantImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.TenantImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TenantImportMediator extends BackoffImportMediator<TenantImportIndexHandler, TenantEngineDto> {

  private TenantFetcher engineEntityFetcher;

  public TenantImportMediator(final TenantImportIndexHandler importIndexHandler,
                              final TenantFetcher engineEntityFetcher,
                              final TenantImportService tenantImportService,
                              final ConfigurationService configurationService,
                              final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = tenantImportService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    final List<TenantEngineDto> entities = engineEntityFetcher.fetchTenants();
    final List<TenantEngineDto> newEntities = importIndexHandler.filterNewOrChangedTenants(entities);

    if (!newEntities.isEmpty()) {
      importService.executeImport(newEntities, importCompleteCallback);
      importIndexHandler.addImportedTenants(newEntities);
    } else {
      importCompleteCallback.run();
    }

    return !newEntities.isEmpty();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.TENANT;
  }

}
