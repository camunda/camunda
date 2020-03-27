/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;


import org.camunda.optimize.dto.engine.HistoricUserOperationLogDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.UserOperationLogInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.handler.UserOperationLogImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.UserOperationLogImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserOperationLogEngineImportMediator
  extends TimestampBasedImportMediator<UserOperationLogImportIndexHandler, HistoricUserOperationLogDto> {

  private UserOperationLogInstanceFetcher engineEntityFetcher;

  @Autowired
  private RunningProcessInstanceWriter runningProcessInstanceWriter;
  @Autowired
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;

  private final EngineContext engineContext;

  public UserOperationLogEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  @PostConstruct
  public void init() {
    importIndexHandler =
      importIndexHandlerRegistry.getUserOperationsLogImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(UserOperationLogInstanceFetcher.class, engineContext);
    importService = new UserOperationLogImportService(
      elasticsearchImportJobExecutor, runningProcessInstanceWriter
    );
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricUserOperationLogDto historicUserOperationsLogDto) {
    return historicUserOperationsLogDto.getTimestamp();
  }

  @Override
  protected List<HistoricUserOperationLogDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchUserOperationLogs(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricUserOperationLogDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchUserOperationLogsForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportUserOperationLogsMaxPageSize();
  }
}
