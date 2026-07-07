/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.MetricEnum.INDEXING_DURATION_METRIC;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PositionBasedImportMediator<
        T extends PositionBasedImportIndexHandler, DTO extends ZeebeRecordDto<?, ?>>
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
    boolean pageIsPresent;
    try {
      pageIsPresent = importNextPage(() -> importCompleted.complete(null));
    } catch (final Exception e) {
      logger.error("Was not able to import next page, skipping this round.", e);
      importCompleted.complete(null);
      pageIsPresent = false;
    }
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

  protected boolean importNextPagePositionBased(
      final List<DTO> entitiesNextPage, final Runnable importCompleteCallback) {
    importIndexHandler.updateLastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime());
    logger.info(
        "Records of type {} from partition {} imported in page: {}",
        getRecordType(),
        getPartitionId(),
        entitiesNextPage.size());
    if (!entitiesNextPage.isEmpty()) {
      final DTO lastImportedEntity = entitiesNextPage.get(entitiesNextPage.size() - 1);
      final long currentPageLastEntityPosition = lastImportedEntity.getPosition();
      final long currentPageLastEntitySequence =
          Optional.ofNullable(lastImportedEntity.getSequence()).orElse(0L);

      final OffsetDateTime startTime = LocalDateUtil.getCurrentDateTime();
      importService.executeImport(
          entitiesNextPage,
          () -> {
            final OffsetDateTime endTime = LocalDateUtil.getCurrentDateTime();
            final long took =
                endTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli();
            final Timer indexingDurationTimer = getIndexingDurationTimer();
            indexingDurationTimer.record(took, MILLISECONDS);

            importIndexHandler.updateLastPersistedEntityPositionAndSequence(
                currentPageLastEntityPosition, currentPageLastEntitySequence);
            importIndexHandler.updateTimestampOfLastPersistedEntity(
                OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(lastImportedEntity.getTimestamp()),
                    ZoneId.systemDefault()));
            OptimizeMetrics.recordOverallEntitiesImportTime(entitiesNextPage);
            importCompleteCallback.run();
          });
      importIndexHandler.updatePendingLastEntityPositionAndSequence(
          currentPageLastEntityPosition, currentPageLastEntitySequence);
    } else {
      importCompleteCallback.run();
    }

    return entitiesNextPage.size()
        >= configurationService.getConfiguredZeebe().getMaxImportPageSize();
  }

  public Timer getIndexingDurationTimer() {
    return OptimizeMetrics.getTimer(INDEXING_DURATION_METRIC, getRecordType(), getPartitionId());
  }

  protected abstract String getRecordType();

  protected abstract Integer getPartitionId();

  private void calculateNewDateUntilIsBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      logger.debug(
          "Maximum idle backoff reached, this mediator will not backoff any further than {}ms.",
          idleBackoffCalculator.getMaximumBackoffMilliseconds());
    }
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    logger.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }
}
