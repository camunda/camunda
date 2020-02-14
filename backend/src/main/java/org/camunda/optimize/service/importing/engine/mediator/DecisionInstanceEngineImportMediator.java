/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
import org.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import org.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.DecisionInstanceImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionInstanceEngineImportMediator
  extends TimestampBasedImportMediator<DecisionInstanceImportIndexHandler, HistoricDecisionInstanceDto> {

  private DecisionInstanceFetcher decisionInstanceFetcher;

  @Autowired
  private DecisionInstanceWriter decisionInstanceWriter;
  @Autowired
  private DecisionDefinitionResolverService decisionDefinitionResolverService;
  @Autowired
  private DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;
  @Autowired
  private DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;
  @Autowired
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;

  private final EngineContext engineContext;

  public DecisionInstanceEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getDecisionInstanceImportIndexHandler(engineContext.getEngineAlias());
    decisionInstanceFetcher = beanFactory.getBean(DecisionInstanceFetcher.class, engineContext);
    importService = new DecisionInstanceImportService(
      elasticsearchImportJobExecutor,
      engineContext,
      decisionInstanceWriter,
      decisionDefinitionResolverService,
      decisionInputImportAdapterProvider,
      decisionOutputImportAdapterProvider
    );
  }

  @Override
  protected List<HistoricDecisionInstanceDto> getEntitiesLastTimestamp() {
    return decisionInstanceFetcher.fetchHistoricDecisionInstances(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected List<HistoricDecisionInstanceDto> getEntitiesNextPage() {
    return decisionInstanceFetcher.fetchHistoricDecisionInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportDecisionInstanceMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricDecisionInstanceDto historicDecisionInstanceDto) {
    return historicDecisionInstanceDto.getEvaluationTime();
  }
}
