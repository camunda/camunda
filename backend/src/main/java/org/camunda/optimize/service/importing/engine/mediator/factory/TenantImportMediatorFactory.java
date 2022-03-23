/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.TenantWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.TenantFetcher;
import org.camunda.optimize.service.importing.engine.mediator.TenantImportMediator;
import org.camunda.optimize.service.importing.engine.service.TenantImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TenantImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final TenantWriter tenantWriter;

  public TenantImportMediatorFactory(final BeanFactory beanFactory,
                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                     final ConfigurationService configurationService,
                                     final TenantWriter tenantWriter) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.tenantWriter = tenantWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(createTenantImportMediator(engineContext));
  }

  public TenantImportMediator createTenantImportMediator(
    EngineContext engineContext) {
    return new TenantImportMediator(
      importIndexHandlerRegistry.getTenantImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(TenantFetcher.class, engineContext),
      new TenantImportService(configurationService, engineContext, tenantWriter),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

}
