/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.util.ImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StoreIndexesEngineImportMediator implements EngineImportMediator {

  @Autowired
  private ImportIndexWriter importIndexWriter;
  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected ImportIndexHandlerRegistry importIndexHandlerRegistry;

  private StoreIndexesEngineImportService importService;

  private OffsetDateTime dateUntilJobCreationIsBlocked;
  protected EngineContext engineContext;

  public StoreIndexesEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    importService = new StoreIndexesEngineImportService(importIndexWriter, elasticsearchImportJobExecutor);
  }

  @Override
  public long getBackoffTimeInMs() {
    long backoffTime = OffsetDateTime.now().until(dateUntilJobCreationIsBlocked, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  @Override
  public void runImport() {
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      List<ImportIndexDto> importIndexes =
        Stream.concat(
          createStreamForHandlers(importIndexHandlerRegistry.getAllEntitiesBasedHandlers(engineContext.getEngineAlias())),
          createStreamForHandlers(importIndexHandlerRegistry.getTimestampBasedHandlers(engineContext.getEngineAlias()))
        )
          .map(ImportIndexHandler::createIndexInformationForStoring)
          .collect(Collectors.toList());
      importService.executeImport(importIndexes);
    } catch (Exception e) {
      log.error("Could not execute import for storing index information!", e);
    }
  }

  @Override
  public boolean canImport() {
    return OffsetDateTime.now().isAfter(dateUntilJobCreationIsBlocked);
  }

  @Override
  public ImportJobExecutor getImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private OffsetDateTime calculateDateUntilJobCreationIsBlocked() {
    return OffsetDateTime.now().plusSeconds(configurationService.getImportIndexAutoStorageIntervalInSec());
  }

  private <T extends ImportIndexHandler> Stream<T> createStreamForHandlers(List<T> handlers) {
    return Optional.ofNullable(handlers)
      .map(Collection::stream)
      .orElseGet(Stream::empty);
  }

  @Override
  public void resetBackoff() {
    this.dateUntilJobCreationIsBlocked = OffsetDateTime.MIN;
  }

}
