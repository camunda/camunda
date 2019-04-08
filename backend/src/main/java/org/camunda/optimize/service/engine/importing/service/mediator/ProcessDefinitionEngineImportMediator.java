/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.ProcessDefinitionFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ProcessDefinitionImportService;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
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

  public ProcessDefinitionEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getProcessDefinitionImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(ProcessDefinitionFetcher.class, engineContext);
    definitionImportService = new ProcessDefinitionImportService(
      processDefinitionWriter,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    AllEntitiesBasedImportPage page = importIndexHandler.getNextPage();
    List<ProcessDefinitionEngineDto> entities = engineEntityFetcher.fetchProcessDefinitions(page);
    List<ProcessDefinitionEngineDto> newEntities = importIndexHandler.filterNewDefinitions(entities);
    importIndexHandler.addImportedDefinitions(newEntities);

    if (!newEntities.isEmpty()) {
      definitionImportService.executeImport(newEntities);
    }
    return !newEntities.isEmpty();
  }

}
