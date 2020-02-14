/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.importing.BackoffImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionImportService;
import org.camunda.optimize.service.importing.page.AllEntitiesBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionEngineImportMediator
  extends BackoffImportMediator<DecisionDefinitionImportIndexHandler> {

  @Autowired
  private DecisionDefinitionWriter processDefinitionWriter;

  private DecisionDefinitionFetcher engineEntityFetcher;
  private DecisionDefinitionImportService definitionImportService;

  private final EngineContext engineContext;

  public DecisionDefinitionEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getDecisionDefinitionImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(DecisionDefinitionFetcher.class, engineContext);
    definitionImportService = new DecisionDefinitionImportService(
      elasticsearchImportJobExecutor, engineContext, processDefinitionWriter
    );
  }

  @Override
  protected boolean importNextPage() {
    AllEntitiesBasedImportPage page = importIndexHandler.getNextPage();
    List<DecisionDefinitionEngineDto> entities = engineEntityFetcher.fetchDecisionDefinitions(page);
    List<DecisionDefinitionEngineDto> newEntities = importIndexHandler.filterNewDefinitions(entities);
    if (!newEntities.isEmpty()) {
      definitionImportService.executeImport(newEntities);
      importIndexHandler.addImportedDefinitions(newEntities);
    }
    return !newEntities.isEmpty();
  }

}
