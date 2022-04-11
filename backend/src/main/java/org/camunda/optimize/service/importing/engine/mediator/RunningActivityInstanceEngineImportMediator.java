/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.RunningActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningActivityInstanceEngineImportMediator
  extends TimestampBasedImportMediator<RunningActivityInstanceImportIndexHandler, HistoricActivityInstanceEngineDto> {

  private final RunningActivityInstanceFetcher engineEntityFetcher;

  public RunningActivityInstanceEngineImportMediator(final RunningActivityInstanceImportIndexHandler importIndexHandler,
                                                     final RunningActivityInstanceFetcher engineEntityFetcher,
                                                     final RunningActivityInstanceImportService importService,
                                                     final ConfigurationService configurationService,
                                                     final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricActivityInstanceEngineDto historicActivityInstanceEngineDto) {
    return historicActivityInstanceEngineDto.getStartTime();
  }

  @Override
  protected List<HistoricActivityInstanceEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchRunningActivityInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricActivityInstanceEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchRunningActivityInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportActivityInstanceMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

}
