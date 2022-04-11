/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningUserTaskInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.RunningUserTaskInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.RunningUserTaskInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningUserTaskInstanceEngineImportMediator
  extends TimestampBasedImportMediator<RunningUserTaskInstanceImportIndexHandler, HistoricUserTaskInstanceDto> {

  private final RunningUserTaskInstanceFetcher engineEntityFetcher;

  public RunningUserTaskInstanceEngineImportMediator(final RunningUserTaskInstanceImportIndexHandler importIndexHandler,
                                                     final RunningUserTaskInstanceFetcher engineEntityFetcher,
                                                     final RunningUserTaskInstanceImportService importService,
                                                     final ConfigurationService configurationService,
                                                     final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    return historicUserTaskInstanceDto.getStartTime();
  }

  @Override
  protected List<HistoricUserTaskInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchRunningUserTaskInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricUserTaskInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchRunningUserTaskInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());
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
