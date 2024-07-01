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
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessDefinitionXmlWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.definition.ProcessDefinitionFetcher;
import io.camunda.optimize.service.importing.engine.fetcher.instance.ProcessDefinitionXmlFetcher;
import io.camunda.optimize.service.importing.engine.mediator.ProcessDefinitionEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.ProcessDefinitionXmlEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionImportService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionXmlImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessDefinitionEngineImportMediatorFactory
    extends AbstractEngineImportMediatorFactory {
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessDefinitionXmlWriter processDefinitionXmlWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public ProcessDefinitionEngineImportMediatorFactory(
      final ProcessDefinitionWriter processDefinitionWriter,
      final BeanFactory beanFactory,
      final ConfigurationService configurationService,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ProcessDefinitionXmlWriter processDefinitionXmlWriter,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.processDefinitionWriter = processDefinitionWriter;
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
        createProcessDefinitionEngineImportMediator(engineContext),
        createProcessDefinitionXmlEngineImportMediator(engineContext));
  }

  private ProcessDefinitionEngineImportMediator createProcessDefinitionEngineImportMediator(
      EngineContext engineContext) {
    return new ProcessDefinitionEngineImportMediator(
        importIndexHandlerRegistry.getProcessDefinitionImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(ProcessDefinitionFetcher.class, engineContext),
        new ProcessDefinitionImportService(
            configurationService,
            engineContext,
            processDefinitionWriter,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }

  private ProcessDefinitionXmlEngineImportMediator createProcessDefinitionXmlEngineImportMediator(
      EngineContext engineContext) {
    return new ProcessDefinitionXmlEngineImportMediator(
        importIndexHandlerRegistry.getProcessDefinitionXmlImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(
            ProcessDefinitionXmlFetcher.class, engineContext, processDefinitionWriter),
        new ProcessDefinitionXmlImportService(
            configurationService, engineContext, processDefinitionXmlWriter, databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
