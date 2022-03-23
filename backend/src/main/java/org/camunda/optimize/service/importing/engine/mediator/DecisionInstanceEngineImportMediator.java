/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.DecisionInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionInstanceEngineImportMediator
  extends TimestampBasedImportMediator<DecisionInstanceImportIndexHandler, HistoricDecisionInstanceDto> {

  private final DecisionInstanceFetcher decisionInstanceFetcher;

  public DecisionInstanceEngineImportMediator(final DecisionInstanceImportIndexHandler importIndexHandler,
                                              final DecisionInstanceFetcher decisionInstanceFetcher,
                                              final DecisionInstanceImportService importService,
                                              final ConfigurationService configurationService,
                                              final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.decisionInstanceFetcher = decisionInstanceFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricDecisionInstanceDto historicDecisionInstanceDto) {
    return historicDecisionInstanceDto.getEvaluationTime();
  }

  @Override
  protected List<HistoricDecisionInstanceDto> getEntitiesNextPage() {
    return decisionInstanceFetcher.fetchHistoricDecisionInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricDecisionInstanceDto> getEntitiesLastTimestamp() {
    return decisionInstanceFetcher.fetchHistoricDecisionInstances(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportDecisionInstanceMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE;
  }

}
