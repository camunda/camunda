/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.micrometer.core.instrument.Timer;
import org.camunda.optimize.OptimizeMetrics;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.camunda.optimize.MetricEnum.INDEXING_DURATION_METRIC;

public abstract class PositionBasedImportMediator<T extends PositionBasedImportIndexHandler,
  DTO extends ZeebeRecordDto<?, ?>>
  implements ImportMediator {

  private final BackoffCalculator errorBackoffCalculator = new BackoffCalculator(10, 1000);
  protected Logger logger = LoggerFactory.getLogger(getClass());
  protected ConfigurationService configurationService;
  protected BackoffCalculator idleBackoffCalculator;
  protected T importIndexHandler;
  protected ImportService<DTO> importService;

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    boolean pageIsPresent = importNextPageWithRetries(importCompleted);
    if (pageIsPresent) {
      idleBackoffCalculator.resetBackoff();
    } else {
      calculateNewDateUntilIsBlocked();
    }
    return importCompleted;
  }

  public long getBackoffTimeInMs() {
    return idleBackoffCalculator.getTimeUntilNextRetry();
  }

  @Override
  public void resetBackoff() {
    idleBackoffCalculator.resetBackoff();
  }

  @Override
  public boolean canImport() {
    boolean canImportNewPage = idleBackoffCalculator.isReadyForNextRetry();
    logger.debug("can import next page [{}]", canImportNewPage);
    return canImportNewPage;
  }

  public T getImportIndexHandler() {
    return importIndexHandler;
  }

  @Override
  public boolean hasPendingImportJobs() {
    return importService.hasPendingImportJobs();
  }

  @Override
  public void shutdown() {
    importService.shutdown();
  }

  protected abstract boolean importNextPage(Runnable importCompleteCallback);

  private boolean importNextPageWithRetries(final CompletableFuture<Void> importCompleteCallback) {
    Boolean result = null;
    while (result == null) {
      try {
        result = importNextPage(() -> importCompleteCallback.complete(null));
      } catch (final Exception e) {
        if (errorBackoffCalculator.isMaximumBackoffReached()) {
          // if max back-off is reached abort retrying and return true to indicate there is new data
          logger.error("Was not able to import next page and reached max backoff, aborting this run.", e);
          importCompleteCallback.complete(null);
          result = true;
        } else {
          long timeToSleep = errorBackoffCalculator.calculateSleepTime();
          logger.error("Was not able to import next page, retrying after sleeping for {}ms.", timeToSleep, e);
          sleep(timeToSleep);
        }
      }
    }
    errorBackoffCalculator.resetBackoff();

    return result;
  }

  protected boolean importNextPagePositionBased(final List<DTO> entitiesNextPage,
                                                final Runnable importCompleteCallback) {
    importIndexHandler.updateLastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime());
    logger.info("Records of type {} imported in page: {}", getRecordType(), entitiesNextPage.size());
    if (!entitiesNextPage.isEmpty()) {
      final DTO lastImportedEntity = entitiesNextPage.get(entitiesNextPage.size() - 1);
      final long currentPageLastEntityPosition = lastImportedEntity.getPosition();
      final long currentPageLastEntitySequence = Optional.ofNullable(lastImportedEntity.getSequence()).orElse(0L);

      getIndexingDurationTimer()
        .record(() -> importService.executeImport(entitiesNextPage, () -> {
          importIndexHandler.updateLastPersistedEntityPositionAndSequence(currentPageLastEntityPosition,
                                                                          currentPageLastEntitySequence);
          importIndexHandler.updateTimestampOfLastPersistedEntity(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastImportedEntity.getTimestamp()), ZoneId.systemDefault()));
          OptimizeMetrics.recordOverallEntitiesImportTime(entitiesNextPage);
          importCompleteCallback.run();
        }));
      importIndexHandler.updatePendingLastEntityPositionAndSequence(currentPageLastEntityPosition, currentPageLastEntitySequence);
    } else {
      importCompleteCallback.run();
    }

    return entitiesNextPage.size() >= configurationService.getConfiguredZeebe().getMaxImportPageSize();
  }

  public Timer getIndexingDurationTimer() {
    return OptimizeMetrics.getTimer(
      INDEXING_DURATION_METRIC,
      getRecordType(),
      getPartitionId()
    );
  }

  protected abstract String getRecordType();

  protected abstract Integer getPartitionId();

  private void calculateNewDateUntilIsBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      logger.debug(
        "Maximum idle backoff reached, this mediator will not backoff any further than {}ms.",
        idleBackoffCalculator.getMaximumBackoffMilliseconds()
      );
    }
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    logger.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }

  private void sleep(final long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.error("Was interrupted from sleep.", e);
      Thread.currentThread().interrupt();
    }
  }

}
