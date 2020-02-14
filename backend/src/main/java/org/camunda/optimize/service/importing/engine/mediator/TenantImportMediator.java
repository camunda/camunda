/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.TenantWriter;
import org.camunda.optimize.service.importing.BackoffImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.TenantFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.handler.TenantImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.TenantImportService;
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
  @Autowired
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;

  private TenantFetcher engineEntityFetcher;
  private TenantImportService tenantImportService;

  private final EngineContext engineContext;

  public TenantImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getTenantImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(TenantFetcher.class, engineContext);
    tenantImportService = new TenantImportService(elasticsearchImportJobExecutor, engineContext, tenantWriter);
  }

  @Override
  protected boolean importNextPage() {
    final List<TenantEngineDto> entities = engineEntityFetcher.fetchTenants();
    final List<TenantEngineDto> newEntities = importIndexHandler.filterNewOrChangedTenants(entities);

    if (!newEntities.isEmpty()) {
      tenantImportService.executeImport(newEntities);
      importIndexHandler.addImportedTenants(newEntities);
    }
    return !newEntities.isEmpty();
  }

}
