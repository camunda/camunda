/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricIdentityLinkLogDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.IdentityLinkLogWriter;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.IdentityLinkLogInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.IdentityLinkLogImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.IdentityLinkLogImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IdentityLinkLogEngineImportMediator
  extends TimestampBasedImportMediator<IdentityLinkLogImportIndexHandler, HistoricIdentityLinkLogDto> {

  private IdentityLinkLogInstanceFetcher engineEntityFetcher;

  @Autowired
  private IdentityLinkLogWriter identityLinkLogWriter;

  private final EngineContext engineContext;

  public IdentityLinkLogEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = importIndexHandlerRegistry.getIdentityLinkImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(IdentityLinkLogInstanceFetcher.class, engineContext);
    importService = new IdentityLinkLogImportService(
      identityLinkLogWriter,
      elasticsearchImportJobExecutor,
      engineContext
    );
  }

  @Override
  protected List<HistoricIdentityLinkLogDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchIdentityLinkLogsForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected List<HistoricIdentityLinkLogDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchIdentityLinkLogs(importIndexHandler.getNextPage());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportIdentityLinkLogsMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricIdentityLinkLogDto historicIdentityLinkLogDto) {
    return historicIdentityLinkLogDto.getTime();
  }
}
