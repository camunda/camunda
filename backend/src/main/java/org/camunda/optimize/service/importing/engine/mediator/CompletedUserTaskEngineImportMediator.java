/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedUserTaskInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.CompletedUserTaskInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.CompletedUserTaskInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedUserTaskEngineImportMediator
  extends TimestampBasedImportMediator<CompletedUserTaskInstanceImportIndexHandler, HistoricUserTaskInstanceDto> {

  private CompletedUserTaskInstanceFetcher engineEntityFetcher;

  public CompletedUserTaskEngineImportMediator(final CompletedUserTaskInstanceImportIndexHandler importIndexHandler,
                                               final CompletedUserTaskInstanceFetcher engineEntityFetcher,
                                               final CompletedUserTaskInstanceImportService importService,
                                               final ConfigurationService configurationService,
                                               final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    return historicUserTaskInstanceDto.getEndTime();
  }

  @Override
  protected List<HistoricUserTaskInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchCompletedUserTaskInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricUserTaskInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchCompletedUserTaskInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportUserTaskInstanceMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

}
