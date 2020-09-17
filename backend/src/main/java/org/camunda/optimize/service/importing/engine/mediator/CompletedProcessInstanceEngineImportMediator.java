/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedProcessInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.CompletedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.CompletedProcessInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedProcessInstanceEngineImportMediator
  extends TimestampBasedImportMediator<CompletedProcessInstanceImportIndexHandler, HistoricProcessInstanceDto> {

  private CompletedProcessInstanceFetcher engineEntityFetcher;

  public CompletedProcessInstanceEngineImportMediator(final CompletedProcessInstanceImportIndexHandler importIndexHandler,
                                                      final CompletedProcessInstanceFetcher engineEntityFetcher,
                                                      final CompletedProcessInstanceImportService importService,
                                                      final ConfigurationService configurationService,
                                                      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricProcessInstanceDto historicProcessInstanceDto) {
    return historicProcessInstanceDto.getEndTime();
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchCompletedProcessInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchCompletedProcessInstances(importIndexHandler.getTimestampOfLastEntity());

  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE;
  }

}
