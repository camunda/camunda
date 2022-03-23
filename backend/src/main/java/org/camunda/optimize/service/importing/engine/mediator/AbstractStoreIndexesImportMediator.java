/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
public abstract class AbstractStoreIndexesImportMediator<T extends ImportService> implements ImportMediator {

  protected T importService;
  protected OffsetDateTime dateUntilJobCreationIsBlocked;
  private ConfigurationService configurationService;

  protected AbstractStoreIndexesImportMediator(T importService,
                                               ConfigurationService configurationService) {
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
    return OffsetDateTime.now().plusSeconds(configurationService.getImportIndexAutoStorageIntervalInSec());
  }

}
