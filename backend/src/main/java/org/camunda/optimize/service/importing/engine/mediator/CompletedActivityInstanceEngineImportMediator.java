/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.CompletedActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.CompletedActivityInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedActivityInstanceEngineImportMediator
  extends TimestampBasedImportMediator<CompletedActivityInstanceImportIndexHandler, HistoricActivityInstanceEngineDto> {

  private CompletedActivityInstanceFetcher engineEntityFetcher;

  public CompletedActivityInstanceEngineImportMediator(final CompletedActivityInstanceImportIndexHandler importIndexHandler,
                                                       final CompletedActivityInstanceFetcher engineEntityFetcher,
                                                       final CompletedActivityInstanceImportService importService,
                                                       final ConfigurationService configurationService,
                                                       final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                       final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected List<HistoricActivityInstanceEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchCompletedActivityInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected List<HistoricActivityInstanceEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchCompletedActivityInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportActivityInstanceMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricActivityInstanceEngineDto dto) {
    return dto.getEndTime();
  }

}
