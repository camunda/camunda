/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.TenantWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.TenantFetcher;
import io.camunda.optimize.service.importing.engine.mediator.TenantImportMediator;
import io.camunda.optimize.service.importing.engine.service.TenantImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class TenantImportMediatorFactory extends AbstractEngineImportMediatorFactory {

  private final TenantWriter tenantWriter;

  public TenantImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final TenantWriter tenantWriter,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.tenantWriter = tenantWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(createTenantImportMediator(engineContext));
  }

  public TenantImportMediator createTenantImportMediator(EngineContext engineContext) {
    return new TenantImportMediator(
        importIndexHandlerRegistry.getTenantImportIndexHandler(engineContext.getEngineAlias()),
        beanFactory.getBean(TenantFetcher.class, engineContext),
        new TenantImportService(configurationService, engineContext, tenantWriter, databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
