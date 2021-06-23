/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.usertask.IdentityLinkLogWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.IdentityLinkLogInstanceFetcher;
import org.camunda.optimize.service.importing.engine.mediator.IdentityLinkLogEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.IdentityLinkLogImportService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class IdentityLinkLogEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final IdentityLinkLogWriter identityLinkLogWriter;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public IdentityLinkLogEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                    final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                    final ConfigurationService configurationService,
                                                    final IdentityLinkLogWriter identityLinkLogWriter,
                                                    final AssigneeCandidateGroupService assigneeCandidateGroupService,
                                                    final ProcessDefinitionResolverService processDefinitionResolverService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.identityLinkLogWriter = identityLinkLogWriter;
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportUserTaskWorkerDataEnabled() ?
      ImmutableList.of(createIdentityLinkLogEngineImportMediator(engineContext))
      : Collections.emptyList();
  }

  private IdentityLinkLogEngineImportMediator createIdentityLinkLogEngineImportMediator(
    final EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new IdentityLinkLogEngineImportMediator(
      importIndexHandlerRegistry.getIdentityLinkImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(IdentityLinkLogInstanceFetcher.class, engineContext),
      new IdentityLinkLogImportService(
        identityLinkLogWriter,
        assigneeCandidateGroupService,
        elasticsearchImportJobExecutor,
        engineContext,
        processDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
