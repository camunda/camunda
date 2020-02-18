/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.BackoffImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.handler.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.ProcessDefinitionImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionEngineImportMediator
  extends BackoffImportMediator<ProcessDefinitionImportIndexHandler> {

  private ProcessDefinitionFetcher engineEntityFetcher;
  private ProcessDefinitionImportService definitionImportService;

  @Autowired
  private ProcessDefinitionWriter processDefinitionWriter;

  private final EngineContext engineContext;

  public ProcessDefinitionEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getProcessDefinitionImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(ProcessDefinitionFetcher.class, engineContext);
    definitionImportService = new ProcessDefinitionImportService(
      elasticsearchImportJobExecutor, engineContext, processDefinitionWriter
    );
  }

  @Override
  protected boolean importNextPage() {
    List<ProcessDefinitionEngineDto> entities = engineEntityFetcher.fetchProcessDefinitions();
    List<ProcessDefinitionEngineDto> newEntities = importIndexHandler.filterNewDefinitions(entities);

    if (!newEntities.isEmpty()) {
      definitionImportService.executeImport(newEntities);
      importIndexHandler.addImportedDefinitions(newEntities);
    }
    return !newEntities.isEmpty();
  }

}
