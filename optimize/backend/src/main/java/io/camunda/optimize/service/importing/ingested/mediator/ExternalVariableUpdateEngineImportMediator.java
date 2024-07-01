/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.mediator;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.importing.ExternalVariableUpdateImportIndexHandler;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.ingested.fetcher.ExternalVariableUpdateInstanceFetcher;
import io.camunda.optimize.service.importing.ingested.service.ExternalVariableUpdateImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateEngineImportMediator
    extends TimestampBasedImportMediator<
        ExternalVariableUpdateImportIndexHandler, ExternalProcessVariableDto> {

  private final ExternalVariableUpdateInstanceFetcher entityFetcher;

  public ExternalVariableUpdateEngineImportMediator(
      final ExternalVariableUpdateImportIndexHandler importIndexHandler,
      final ExternalVariableUpdateInstanceFetcher entityFetcher,
      final ExternalVariableUpdateImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.entityFetcher = entityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(
      final ExternalProcessVariableDto historicVariableUpdateInstanceDto) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(historicVariableUpdateInstanceDto.getIngestionTimestamp()),
        ZoneId.systemDefault());
  }

  @Override
  protected List<ExternalProcessVariableDto> getEntitiesNextPage() {
    return entityFetcher.fetchVariableInstanceUpdates(importIndexHandler.getNextPage());
  }

  @Override
  protected List<ExternalProcessVariableDto> getEntitiesLastTimestamp() {
    return entityFetcher.fetchVariableInstanceUpdates(
        importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService
        .getExternalVariableConfiguration()
        .getImportConfiguration()
        .getMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }
}
