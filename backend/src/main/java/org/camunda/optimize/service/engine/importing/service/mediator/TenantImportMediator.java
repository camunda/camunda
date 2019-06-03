/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.TenantFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.TenantImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.TenantImportService;
import org.camunda.optimize.service.es.writer.TenantWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TenantImportMediator extends BackoffImportMediator<TenantImportIndexHandler> {

  @Autowired
  private TenantWriter tenantWriter;

  private TenantFetcher engineEntityFetcher;
  private TenantImportService tenantImportService;

  public TenantImportMediator(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getTenantImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(TenantFetcher.class, engineContext);
    tenantImportService = new TenantImportService(elasticsearchImportJobExecutor, engineContext, tenantWriter);
  }

  @Override
  protected boolean importNextEnginePage() {
    final AllEntitiesBasedImportPage page = importIndexHandler.getNextPage();
    final List<TenantEngineDto> entities = engineEntityFetcher.fetchTenants(page);
    final List<TenantEngineDto> newEntities = importIndexHandler.filterNewOrChangedTenants(entities);
    if (!newEntities.isEmpty()) {
      tenantImportService.executeImport(newEntities);
      importIndexHandler.addImportedTenants(newEntities);
    }
    return !newEntities.isEmpty();
  }

}
