/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.engine.HistoricIdentityLinkLogDto;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.IdentityLinkLogInstanceFetcher;
import io.camunda.optimize.service.importing.engine.handler.IdentityLinkLogImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.IdentityLinkLogImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IdentityLinkLogEngineImportMediator
    extends TimestampBasedImportMediator<
        IdentityLinkLogImportIndexHandler, HistoricIdentityLinkLogDto> {

  private final IdentityLinkLogInstanceFetcher engineEntityFetcher;

  public IdentityLinkLogEngineImportMediator(
      final IdentityLinkLogImportIndexHandler importIndexHandler,
      final IdentityLinkLogInstanceFetcher engineEntityFetcher,
      final IdentityLinkLogImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(
      final HistoricIdentityLinkLogDto historicIdentityLinkLogDto) {
    return historicIdentityLinkLogDto.getTime();
  }

  @Override
  protected List<HistoricIdentityLinkLogDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchIdentityLinkLogs(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricIdentityLinkLogDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchIdentityLinkLogsForTimestamp(
        importIndexHandler.getTimestampOfLastEntity());
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
