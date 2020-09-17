/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricIdentityLinkLogDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.IdentityLinkLogInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.IdentityLinkLogImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.IdentityLinkLogImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IdentityLinkLogEngineImportMediator
  extends TimestampBasedImportMediator<IdentityLinkLogImportIndexHandler, HistoricIdentityLinkLogDto> {

  private IdentityLinkLogInstanceFetcher engineEntityFetcher;

  public IdentityLinkLogEngineImportMediator(final IdentityLinkLogImportIndexHandler importIndexHandler,
                                             final IdentityLinkLogInstanceFetcher engineEntityFetcher,
                                             final IdentityLinkLogImportService importService,
                                             final ConfigurationService configurationService,
                                             final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricIdentityLinkLogDto historicIdentityLinkLogDto) {
    return historicIdentityLinkLogDto.getTime();
  }

  @Override
  protected List<HistoricIdentityLinkLogDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchIdentityLinkLogs(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricIdentityLinkLogDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchIdentityLinkLogsForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportIdentityLinkLogsMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

}
