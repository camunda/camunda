/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.engine.HistoricUserOperationLogDto;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.UserOperationLogFetcher;
import io.camunda.optimize.service.importing.engine.handler.UserOperationLogImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.UserOperationLogImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserOperationLogEngineImportMediator
    extends TimestampBasedImportMediator<
    UserOperationLogImportIndexHandler, HistoricUserOperationLogDto> {

  private final UserOperationLogFetcher engineEntityFetcher;

  public UserOperationLogEngineImportMediator(
      final UserOperationLogImportIndexHandler importIndexHandler,
      final UserOperationLogFetcher engineEntityFetcher,
      final UserOperationLogImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(
      final HistoricUserOperationLogDto historicUserOperationsLogDto) {
    return historicUserOperationsLogDto.getTimestamp();
  }

  @Override
  protected List<HistoricUserOperationLogDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchUserOperationLogs(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricUserOperationLogDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchUserOperationLogsForTimestamp(
        importIndexHandler.getTimestampOfLastEntity());
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
