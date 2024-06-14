/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.engine.TenantEngineDto;
import io.camunda.optimize.service.importing.BackoffImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.TenantFetcher;
import io.camunda.optimize.service.importing.engine.handler.TenantImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.TenantImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TenantImportMediator
    extends BackoffImportMediator<TenantImportIndexHandler, TenantEngineDto> {

  private final TenantFetcher engineEntityFetcher;

  public TenantImportMediator(
      final TenantImportIndexHandler importIndexHandler,
      final TenantFetcher engineEntityFetcher,
      final TenantImportService tenantImportService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, tenantImportService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    final List<TenantEngineDto> entities = engineEntityFetcher.fetchTenants();
    final List<TenantEngineDto> newEntities =
        importIndexHandler.filterNewOrChangedTenants(entities);

    if (!newEntities.isEmpty()) {
      importService.executeImport(
          filterEntitiesFromExcludedTenants(newEntities), importCompleteCallback);
      importIndexHandler.addImportedTenants(newEntities);
    } else {
      importCompleteCallback.run();
    }
    // It is correct to check here for newEntities (as opposed to the filtered entities) for the
    // case in which the
    // current page only contained data from excluded tenants. In this case filteredEntities would
    // be empty, but
    // potentially more pages with tenant data could exist
    return !newEntities.isEmpty();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.TENANT;
  }
}
