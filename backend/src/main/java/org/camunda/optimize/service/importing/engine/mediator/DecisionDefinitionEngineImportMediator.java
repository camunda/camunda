/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionEngineImportMediator
  extends TimestampBasedImportMediator<DecisionDefinitionImportIndexHandler, DecisionDefinitionEngineDto> {

  private DecisionDefinitionFetcher engineEntityFetcher;

  @Autowired
  private DecisionDefinitionWriter decisionDefinitionWriter;
  @Autowired
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;

  private final EngineContext engineContext;

  public DecisionDefinitionEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler =
      importIndexHandlerRegistry.getDecisionDefinitionImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(DecisionDefinitionFetcher.class, engineContext);
    importService = new DecisionDefinitionImportService(
      elasticsearchImportJobExecutor, engineContext, decisionDefinitionWriter
    );
  }

  @Override
  protected OffsetDateTime getTimestamp(final DecisionDefinitionEngineDto definitionEngineDto) {
    return definitionEngineDto.getDeploymentTime();
  }

  @Override
  protected List<DecisionDefinitionEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchDefinitions(importIndexHandler.getNextPage());
  }

  @Override
  protected List<DecisionDefinitionEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchDefinitionsForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportDecisionDefinitionMaxPageSize();
  }
}
