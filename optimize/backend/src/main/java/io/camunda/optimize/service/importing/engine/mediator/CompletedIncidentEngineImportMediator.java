/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.CompletedIncidentFetcher;
import io.camunda.optimize.service.importing.engine.handler.CompletedIncidentImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.incident.CompletedIncidentImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedIncidentEngineImportMediator
    extends TimestampBasedImportMediator<
        CompletedIncidentImportIndexHandler, HistoricIncidentEngineDto> {

  private final CompletedIncidentFetcher engineEntityFetcher;

  public CompletedIncidentEngineImportMediator(
      final CompletedIncidentImportIndexHandler importIndexHandler,
      final CompletedIncidentFetcher engineEntityFetcher,
      final CompletedIncidentImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricIncidentEngineDto historicIncidentEngineDto) {
    return historicIncidentEngineDto.getEndTime();
  }

  @Override
  protected List<HistoricIncidentEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchCompletedIncidents(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricIncidentEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchCompletedIncidentsForTimestamp(
        importIndexHandler.getTimestampOfLastEntity());
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
