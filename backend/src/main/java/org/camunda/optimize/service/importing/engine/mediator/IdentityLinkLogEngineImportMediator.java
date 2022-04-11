/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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

  private final IdentityLinkLogInstanceFetcher engineEntityFetcher;

  public IdentityLinkLogEngineImportMediator(final IdentityLinkLogImportIndexHandler importIndexHandler,
                                             final IdentityLinkLogInstanceFetcher engineEntityFetcher,
                                             final IdentityLinkLogImportService importService,
                                             final ConfigurationService configurationService,
                                             final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
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
