/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.usertask.IdentityLinkLogWriter;
import io.camunda.optimize.service.identity.PlatformUserTaskIdentityCache;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.IdentityLinkLogInstanceFetcher;
import io.camunda.optimize.service.importing.engine.mediator.IdentityLinkLogEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.IdentityLinkLogImportService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaPlatformCondition.class)
public class IdentityLinkLogEngineImportMediatorFactory
    extends AbstractEngineImportMediatorFactory {

  private final IdentityLinkLogWriter identityLinkLogWriter;
  private final PlatformUserTaskIdentityCache platformUserTaskIdentityCache;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public IdentityLinkLogEngineImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final IdentityLinkLogWriter identityLinkLogWriter,
      final PlatformUserTaskIdentityCache platformUserTaskIdentityCache,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.identityLinkLogWriter = identityLinkLogWriter;
    this.platformUserTaskIdentityCache = platformUserTaskIdentityCache;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportUserTaskWorkerDataEnabled()
        ? List.of(createIdentityLinkLogEngineImportMediator(engineContext))
        : Collections.emptyList();
  }

  private IdentityLinkLogEngineImportMediator createIdentityLinkLogEngineImportMediator(
      final EngineContext engineContext) {
    return new IdentityLinkLogEngineImportMediator(
        importIndexHandlerRegistry.getIdentityLinkImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(IdentityLinkLogInstanceFetcher.class, engineContext),
        new IdentityLinkLogImportService(
            configurationService,
            identityLinkLogWriter,
            platformUserTaskIdentityCache,
            engineContext,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
