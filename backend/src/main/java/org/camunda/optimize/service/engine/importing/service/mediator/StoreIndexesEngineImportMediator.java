/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.util.ImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
  protected ImportIndexHandlerProvider provider;

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
  public void importNextPage() {
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      List<ImportIndexDto> importIndexes = getIndexesToStore();
      importService.executeImport(importIndexes);
    } catch (Exception e) {
      log.error("Could execute import for storing index information!", e);
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

  private List<ImportIndexDto> getIndexesToStore() {
    return Stream
      .concat(getAllEntitiesBasedImportIndexes().stream(), getDefinitionBasedImportIndexes().stream())
      .collect(Collectors.toList());
  }

  private List<AllEntitiesBasedImportIndexDto> getAllEntitiesBasedImportIndexes() {
    List<AllEntitiesBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();

    List<AllEntitiesBasedImportIndexHandler> allEntitiesBasedHandlers = provider.getAllEntitiesBasedHandlers(
      engineContext.getEngineAlias());

    if (allEntitiesBasedHandlers != null) {
      for (AllEntitiesBasedImportIndexHandler importIndexHandler : allEntitiesBasedHandlers) {
        allEntitiesBasedImportIndexes.add(importIndexHandler.createIndexInformationForStoring());
      }

      provider
        .getAllScrollBasedHandlers(engineContext.getEngineAlias())
        .forEach(handler -> allEntitiesBasedImportIndexes.add(handler.createIndexInformationForStoring()));
    }

    return allEntitiesBasedImportIndexes;
  }

  private List<TimestampBasedImportIndexDto> getDefinitionBasedImportIndexes() {
    List<TimestampBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();

    final List<TimestampBasedImportIndexHandler> definitionBasedHandlers = provider.getDefinitionBasedHandlers(
      engineContext.getEngineAlias()
    );
    if (definitionBasedHandlers != null) {
      for (TimestampBasedImportIndexHandler importIndexHandler : definitionBasedHandlers) {
        allEntitiesBasedImportIndexes.add(importIndexHandler.createIndexInformationForStoring());
      }
    }

    return allEntitiesBasedImportIndexes;
  }

  @Override
  public void resetBackoff() {
    disableBlocking();
  }

  public void disableBlocking() {
    this.dateUntilJobCreationIsBlocked = OffsetDateTime.MIN;
  }
}
