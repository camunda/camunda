/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;


import org.camunda.optimize.dto.engine.HistoricUserOperationLogDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.UserOperationLogFetcher;
import org.camunda.optimize.service.importing.engine.handler.UserOperationLogImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.UserOperationLogImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserOperationLogEngineImportMediator
  extends TimestampBasedImportMediator<UserOperationLogImportIndexHandler, HistoricUserOperationLogDto> {

  private final UserOperationLogFetcher engineEntityFetcher;

  public UserOperationLogEngineImportMediator(final UserOperationLogImportIndexHandler importIndexHandler,
                                              final UserOperationLogFetcher engineEntityFetcher,
                                              final UserOperationLogImportService importService,
                                              final ConfigurationService configurationService,
                                              final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricUserOperationLogDto historicUserOperationsLogDto) {
    return historicUserOperationsLogDto.getTimestamp();
  }

  @Override
  protected List<HistoricUserOperationLogDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchUserOperationLogs(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricUserOperationLogDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchUserOperationLogsForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportUserOperationLogsMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

}
