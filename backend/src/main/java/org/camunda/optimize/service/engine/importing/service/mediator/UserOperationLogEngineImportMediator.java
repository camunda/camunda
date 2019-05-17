/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.UserOperationLogEntryEngineDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.UserOperationLogEntryFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.UserOperationLogInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.service.UserOperationLogImportService;
import org.camunda.optimize.service.es.writer.UserOperationsLogEntryWriter;
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
  extends TimestampBasedImportMediator<UserOperationLogInstanceImportIndexHandler, UserOperationLogEntryEngineDto> {

  private UserOperationLogEntryFetcher engineEntityFetcher;

  @Autowired
  private UserOperationsLogEntryWriter userOperationsLogEntryWriter;
  @Autowired
  private ImportAdapterProvider importAdapterProvider;

  public UserOperationLogEngineImportMediator(EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getUserOperationLogImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(UserOperationLogEntryFetcher.class, engineContext);
    importService = new UserOperationLogImportService(
      userOperationsLogEntryWriter, elasticsearchImportJobExecutor, engineContext
    );
  }

  @Override
  protected List<UserOperationLogEntryEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchUserOperationLogEntries(importIndexHandler.getNextPage());
  }

  @Override
  protected List<UserOperationLogEntryEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchUserOperationLogEntriesForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportUserOperationLogEntryMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final UserOperationLogEntryEngineDto userOperationLogEntryEngineDto) {
    return userOperationLogEntryEngineDto.getTimestamp();
  }
}
