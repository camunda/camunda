/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.mediator;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.ingested.fetcher.ExternalVariableUpdateInstanceFetcher;
import io.camunda.optimize.service.importing.ingested.handler.ExternalVariableUpdateImportIndexHandler;
import io.camunda.optimize.service.importing.ingested.service.ExternalVariableUpdateImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateImportMediator implements ImportMediator {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ExternalVariableUpdateImportMediator.class);
  private final ConfigurationService configurationService;
  private final BackoffCalculator idleBackoffCalculator;
  private final ExternalVariableUpdateImportIndexHandler importIndexHandler;
  private final ImportService<ExternalProcessVariableDto> importService;
  private final BackoffCalculator errorBackoffCalculator = new BackoffCalculator(10, 1000);
  private final ExternalVariableUpdateInstanceFetcher entityFetcher;
  private int countOfImportedEntitiesWithLastEntityTimestamp = 0;

  public ExternalVariableUpdateImportMediator(
      final ExternalVariableUpdateImportIndexHandler importIndexHandler,
      final ExternalVariableUpdateInstanceFetcher entityFetcher,
      final ExternalVariableUpdateImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.entityFetcher = entityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    final boolean pageIsPresent = importNextPageRetryOnError(importCompleted);
    if (pageIsPresent) {
      idleBackoffCalculator.resetBackoff();
    } else {
      calculateNewDateUntilIsBlocked();
    }
    return importCompleted;
  }

  @Override
  public long getBackoffTimeInMs() {
    return idleBackoffCalculator.getTimeUntilNextRetry();
  }

  @Override
  public void resetBackoff() {
    idleBackoffCalculator.resetBackoff();
  }

  @Override
  public boolean canImport() {
    final boolean canImportNewPage = idleBackoffCalculator.isReadyForNextRetry();
    LOG.debug("can import next page [{}]", canImportNewPage);
    return canImportNewPage;
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
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

  private OffsetDateTime getTimestamp(
      final ExternalProcessVariableDto historicVariableUpdateInstanceDto) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(historicVariableUpdateInstanceDto.getIngestionTimestamp()),
        ZoneId.systemDefault());
  }

  private List<ExternalProcessVariableDto> getEntitiesNextPage() {
    return entityFetcher.fetchVariableInstanceUpdates(importIndexHandler.getNextPage());
  }

  private List<ExternalProcessVariableDto> getEntitiesLastTimestamp() {
    return entityFetcher.fetchVariableInstanceUpdates(
        importIndexHandler.getTimestampOfLastEntity());
  }

  private int getMaxPageSize() {
    return configurationService
        .getExternalVariableConfiguration()
        .getImportConfiguration()
        .getMaxPageSize();
  }

  private boolean importNextPage(final Runnable importCompleteCallback) {
    return importNextPageTimestampBased(
        getEntitiesLastTimestamp(),
        getEntitiesNextPage(),
        getMaxPageSize(),
        importCompleteCallback);
  }

  private boolean importNextPageTimestampBased(
      final List<ExternalProcessVariableDto> entitiesLastTimestamp,
      final List<ExternalProcessVariableDto> entitiesNextPage,
      final int maxPageSize,
      final Runnable importCompleteCallback) {
    importIndexHandler.updateLastImportExecutionTimestamp();
    if (!entitiesNextPage.isEmpty()) {
      final List<ExternalProcessVariableDto> allEntities = new ArrayList<>();
      if (entitiesLastTimestamp.size() > countOfImportedEntitiesWithLastEntityTimestamp) {
        allEntities.addAll(entitiesLastTimestamp);
      }
      allEntities.addAll(entitiesNextPage);

      final OffsetDateTime currentPageLastEntityTimestamp =
          getTimestamp(entitiesNextPage.get(entitiesNextPage.size() - 1));
      importService.executeImport(
          allEntities,
          () -> {
            importIndexHandler.updateTimestampOfLastEntity(currentPageLastEntityTimestamp);
            importCompleteCallback.run();
          });
      countOfImportedEntitiesWithLastEntityTimestamp =
          (int)
              entitiesNextPage.stream()
                  .filter(entity -> getTimestamp(entity).equals(currentPageLastEntityTimestamp))
                  .count();
      importIndexHandler.updatePendingTimestampOfLastEntity(currentPageLastEntityTimestamp);
    } else if (entitiesLastTimestamp.size() > countOfImportedEntitiesWithLastEntityTimestamp) {
      countOfImportedEntitiesWithLastEntityTimestamp = entitiesLastTimestamp.size();
      importService.executeImport(entitiesLastTimestamp, importCompleteCallback);
    } else {
      importCompleteCallback.run();
    }

    return entitiesNextPage.size() >= maxPageSize;
  }

  private boolean importNextPageRetryOnError(final CompletableFuture<Void> importCompleteCallback) {
    Boolean result = null;
    try {
      while (result == null) {
        try {
          result = importNextPage(() -> importCompleteCallback.complete(null));
        } catch (final IllegalStateException e) {
          throw e;
        } catch (final Exception e) {
          if (errorBackoffCalculator.isMaximumBackoffReached()) {
            // if max back-off is reached abort retrying and return true to indicate there is new
            // data
            LOG.error(
                "Was not able to import next page and reached max backoff, aborting this run.", e);
            importCompleteCallback.complete(null);
            result = true;
          } else {
            final long timeToSleep = errorBackoffCalculator.calculateSleepTime();
            LOG.error(
                "Was not able to import next page, retrying after sleeping for {}ms.",
                timeToSleep,
                e);
            Thread.sleep(timeToSleep);
          }
        }
      }
    } catch (final InterruptedException e) {
      LOG.warn("Was interrupted while importing next page.", e);
      Thread.currentThread().interrupt();
      return false;
    }
    errorBackoffCalculator.resetBackoff();

    return result;
  }

  private void calculateNewDateUntilIsBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      LOG.debug(
          "Maximum idle backoff reached, this mediator will not backoff any further than {}ms.",
          idleBackoffCalculator.getMaximumBackoffMilliseconds());
    }
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    LOG.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }
}
