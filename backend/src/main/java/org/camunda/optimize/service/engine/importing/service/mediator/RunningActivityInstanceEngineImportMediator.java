/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventService;
import org.camunda.optimize.service.engine.importing.fetcher.instance.RunningActivityInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.RunningActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningActivityInstanceEngineImportMediator
  extends TimestampBasedImportMediator<RunningActivityInstanceImportIndexHandler, HistoricActivityInstanceEngineDto> {

  private RunningActivityInstanceFetcher engineEntityFetcher;

  @Autowired
  private RunningActivityInstanceWriter runningActivityInstanceWriter;
  @Autowired
  private CamundaEventService camundaEventService;
  private RunningActivityInstanceImportService runningActivityInstanceImportService;

  public RunningActivityInstanceEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getRunningActivityInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(RunningActivityInstanceFetcher.class, engineContext);
    importService = new RunningActivityInstanceImportService(
      runningActivityInstanceWriter, camundaEventService, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  protected List<HistoricActivityInstanceEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchRunningActivityInstancesForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected List<HistoricActivityInstanceEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchRunningActivityInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportActivityInstanceMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricActivityInstanceEngineDto historicActivityInstanceEngineDto) {
    return historicActivityInstanceEngineDto.getStartTime();
  }
}
