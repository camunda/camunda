/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.RunningUserTaskInstanceWriter;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningUserTaskInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.RunningUserTaskInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.RunningUserTaskInstanceImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningUserTaskInstanceEngineImportMediator
  extends TimestampBasedImportMediator<RunningUserTaskInstanceImportIndexHandler, HistoricUserTaskInstanceDto> {

  private RunningUserTaskInstanceFetcher engineEntityFetcher;

  @Autowired
  private RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;

  private final EngineContext engineContext;

  public RunningUserTaskInstanceEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getRunningUserTaskInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(RunningUserTaskInstanceFetcher.class, engineContext);
    importService = new RunningUserTaskInstanceImportService(
      runningUserTaskInstanceWriter,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  protected List<HistoricUserTaskInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchRunningUserTaskInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected List<HistoricUserTaskInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchRunningUserTaskInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportUserTaskInstanceMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    return historicUserTaskInstanceDto.getStartTime();
  }
}
