/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.util.ImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

@Component
@Slf4j
public class StoreEventProcessingProgressMediator implements EngineImportMediator {

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
      importService.executeImport(Collections.singletonList(
        importIndexHandlerRegistry.getExternalEventTraceImportIndexHandler().createIndexInformationForStoring()
      ));
    } catch (Exception e) {
      log.error("Could not execute import for storing event processing progress!", e);
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

  @Override
  public void resetBackoff() {
    this.dateUntilJobCreationIsBlocked = OffsetDateTime.MIN;
  }

}
