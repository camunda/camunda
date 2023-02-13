/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.definition.ProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.mediator.ProcessDefinitionEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.ProcessDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDefinitionEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessDefinitionXmlWriter processDefinitionXmlWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public ProcessDefinitionEngineImportMediatorFactory(final ProcessDefinitionWriter processDefinitionWriter,
                                                      final BeanFactory beanFactory,
                                                      final ConfigurationService configurationService,
                                                      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                      final ProcessDefinitionXmlWriter processDefinitionXmlWriter,
                                                      final ProcessDefinitionResolverService processDefinitionResolverService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.processDefinitionWriter = processDefinitionWriter;
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
      createProcessDefinitionEngineImportMediator(engineContext),
      createProcessDefinitionXmlEngineImportMediator(engineContext)
    );
  }

  private ProcessDefinitionEngineImportMediator createProcessDefinitionEngineImportMediator(
    EngineContext engineContext) {
    return new ProcessDefinitionEngineImportMediator(
      importIndexHandlerRegistry.getProcessDefinitionImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(ProcessDefinitionFetcher.class, engineContext),
      new ProcessDefinitionImportService(
        configurationService, engineContext, processDefinitionWriter, processDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

  private ProcessDefinitionXmlEngineImportMediator createProcessDefinitionXmlEngineImportMediator(EngineContext engineContext) {
    return new ProcessDefinitionXmlEngineImportMediator(
      importIndexHandlerRegistry.getProcessDefinitionXmlImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(ProcessDefinitionXmlFetcher.class, engineContext, processDefinitionWriter),
      new ProcessDefinitionXmlImportService(configurationService, engineContext, processDefinitionXmlWriter),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
