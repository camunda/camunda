/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.VariableUpdateInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.VariableUpdateInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.VariableUpdateInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableUpdateEngineImportMediator
  extends TimestampBasedImportMediator<VariableUpdateInstanceImportIndexHandler, HistoricVariableUpdateInstanceDto> {

  private VariableUpdateInstanceFetcher engineEntityFetcher;

  public VariableUpdateEngineImportMediator(final VariableUpdateInstanceImportIndexHandler importIndexHandler,
                                            final VariableUpdateInstanceFetcher engineEntityFetcher,
                                            final VariableUpdateInstanceImportService importService,
                                            final ConfigurationService configurationService,
                                            final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricVariableUpdateInstanceDto historicVariableUpdateInstanceDto) {
    return historicVariableUpdateInstanceDto.getTime();
  }

  @Override
  protected List<HistoricVariableUpdateInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchVariableInstanceUpdates(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricVariableUpdateInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchVariableInstanceUpdates(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportVariableInstanceMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

}
