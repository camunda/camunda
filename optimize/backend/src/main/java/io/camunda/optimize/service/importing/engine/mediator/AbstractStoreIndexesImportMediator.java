/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public abstract class AbstractStoreIndexesImportMediator<T extends ImportService>
    implements ImportMediator {

  protected T importService;
  protected OffsetDateTime dateUntilJobCreationIsBlocked;
  private ConfigurationService configurationService;

  protected AbstractStoreIndexesImportMediator(
      T importService, ConfigurationService configurationService) {
    this.configurationService = configurationService;
    this.dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    this.importService = importService;
  }

  @Override
  public long getBackoffTimeInMs() {
    long backoffTime = OffsetDateTime.now().until(dateUntilJobCreationIsBlocked, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  @Override
  public void resetBackoff() {
    this.dateUntilJobCreationIsBlocked = OffsetDateTime.MIN;
  }

  @Override
  public boolean canImport() {
    return OffsetDateTime.now().isAfter(dateUntilJobCreationIsBlocked);
  }

  @Override
  public boolean hasPendingImportJobs() {
    return importService.hasPendingImportJobs();
  }

  @Override
  public void shutdown() {
    importService.shutdown();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.IMPORT_META_DATA;
  }

  protected OffsetDateTime calculateDateUntilJobCreationIsBlocked() {
    return OffsetDateTime.now()
        .plusSeconds(configurationService.getImportIndexAutoStorageIntervalInSec());
  }
}
