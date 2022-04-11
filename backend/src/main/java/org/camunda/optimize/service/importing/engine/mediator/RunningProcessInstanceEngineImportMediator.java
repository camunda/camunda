/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningProcessInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.RunningProcessInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningProcessInstanceEngineImportMediator
  extends TimestampBasedImportMediator<RunningProcessInstanceImportIndexHandler, HistoricProcessInstanceDto> {

  private final RunningProcessInstanceFetcher engineEntityFetcher;

  public RunningProcessInstanceEngineImportMediator(final RunningProcessInstanceImportIndexHandler importIndexHandler,
                                                    final RunningProcessInstanceFetcher engineEntityFetcher,
                                                    final RunningProcessInstanceImportService importService,
                                                    final ConfigurationService configurationService,
                                                    final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricProcessInstanceDto historicProcessInstanceDto) {
    return historicProcessInstanceDto.getStartTime();
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchRunningProcessInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchRunningProcessInstances(importIndexHandler.getTimestampOfLastEntity());
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
