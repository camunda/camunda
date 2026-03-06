/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import static io.camunda.optimize.MetricEnum.INDEXING_DURATION_METRIC;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.zeebe.ZeebeGenericRecordDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeIncidentImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeVariableImportService;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeRecordFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeRecordImportIndexHandler;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single mediator that fetches one page of mixed Zeebe records from the combined index and
 * dispatches them to the relevant per-type import services in one pass. The four import services
 * ({@link ZeebeProcessInstanceImportService}, {@link ZeebeVariableImportService}, {@link
 * ZeebeIncidentImportService}, {@link ZeebeUserTaskImportService}) each receive only the records
 * that belong to their value type, converted to the appropriate typed DTO.
 *
 * <p>This replaces the four separate mediators (ZeebeProcessInstanceImportMediator,
 * ZeebeVariableImportMediator, ZeebeIncidentImportMediator, ZeebeUserTaskImportMediator) and their
 * individual fetchers.
 */
public class ZeebeCombinedImportMediator implements ImportMediator {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeCombinedImportMediator.class);

  private final ZeebeRecordImportIndexHandler importIndexHandler;
  private final ZeebeRecordFetcher fetcher;
  private final ZeebeProcessInstanceImportService processInstanceImportService;
  private final ZeebeVariableImportService variableImportService;
  private final ZeebeIncidentImportService incidentImportService;
  private final ZeebeUserTaskImportService userTaskImportService;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final BackoffCalculator idleBackoffCalculator;
  private final BackoffCalculator errorBackoffCalculator = new BackoffCalculator(10, 1000);

  public ZeebeCombinedImportMediator(
      final ZeebeRecordImportIndexHandler importIndexHandler,
      final ZeebeRecordFetcher fetcher,
      final ZeebeProcessInstanceImportService processInstanceImportService,
      final ZeebeVariableImportService variableImportService,
      final ZeebeIncidentImportService incidentImportService,
      final ZeebeUserTaskImportService userTaskImportService,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.fetcher = fetcher;
    this.processInstanceImportService = processInstanceImportService;
    this.variableImportService = variableImportService;
    this.incidentImportService = incidentImportService;
    this.userTaskImportService = userTaskImportService;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    final boolean pageIsPresent = importNextPageWithRetries(importCompleted);
    if (pageIsPresent) {
      idleBackoffCalculator.resetBackoff();
    } else {
      calculateNewBackoffUntilBlocked();
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
    return processInstanceImportService.hasPendingImportJobs()
        || variableImportService.hasPendingImportJobs()
        || incidentImportService.hasPendingImportJobs()
        || userTaskImportService.hasPendingImportJobs();
  }

  @Override
  public void shutdown() {
    processInstanceImportService.shutdown();
    variableImportService.shutdown();
    incidentImportService.shutdown();
    userTaskImportService.shutdown();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE;
  }

  // ---- private helpers ----

  private boolean importNextPageWithRetries(final CompletableFuture<Void> importCompleted) {
    Boolean result = null;
    while (result == null) {
      try {
        result = importNextPage(() -> importCompleted.complete(null));
      } catch (final Exception e) {
        if (errorBackoffCalculator.isMaximumBackoffReached()) {
          LOG.error(
              "Was not able to import next page and reached max backoff, aborting this run.", e);
          importCompleted.complete(null);
          result = true;
        } else {
          final long timeToSleep = errorBackoffCalculator.calculateSleepTime();
          LOG.error(
              "Was not able to import next page, retrying after sleeping for {}ms.",
              timeToSleep,
              e);
          sleep(timeToSleep);
        }
      }
    }
    errorBackoffCalculator.resetBackoff();
    return result;
  }

  private boolean importNextPage(final Runnable importCompleteCallback) {
    importIndexHandler.updateLastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime());

    final PositionBasedImportPage page = importIndexHandler.getNextPage();
    final List<ZeebeGenericRecordDto> records =
        fetcher.getZeebeRecordsForPrefixAndPartitionFrom(page);

    LOG.info(
        "Combined Zeebe records from partition {} imported in page: {}",
        fetcher.getPartitionId(),
        records.size());

    if (!records.isEmpty()) {
      final ZeebeGenericRecordDto lastRecord = records.get(records.size() - 1);
      final long lastPosition = lastRecord.getPosition();
      final long lastSequence = Optional.ofNullable(lastRecord.getSequence()).orElse(0L);

      final OffsetDateTime startTime = LocalDateUtil.getCurrentDateTime();

      // Route records to each import service by value type
      dispatchToServices(
          records,
          () -> {
            final OffsetDateTime endTime = LocalDateUtil.getCurrentDateTime();
            final long took =
                endTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli();
            getIndexingDurationTimer().record(took, MILLISECONDS);

            importIndexHandler.updateLastPersistedEntityPositionAndSequence(
                lastPosition, lastSequence);
            importIndexHandler.updateTimestampOfLastPersistedEntity(
                OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(lastRecord.getTimestamp()), ZoneId.systemDefault()));
            importCompleteCallback.run();
          });

      importIndexHandler.updatePendingLastEntityPositionAndSequence(lastPosition, lastSequence);
    } else {
      importCompleteCallback.run();
    }

    return records.size() >= configurationService.getConfiguredZeebe().getMaxImportPageSize();
  }

  private void dispatchToServices(
      final List<ZeebeGenericRecordDto> records, final Runnable allServicesCompleteCallback) {
    final List<ZeebeProcessInstanceRecordDto> processInstanceRecords =
        filterAndConvert(records, ValueType.PROCESS_INSTANCE, ZeebeProcessInstanceRecordDto.class);
    final List<ZeebeVariableRecordDto> variableRecords =
        filterAndConvert(records, ValueType.VARIABLE, ZeebeVariableRecordDto.class);
    final List<ZeebeIncidentRecordDto> incidentRecords =
        filterAndConvert(records, ValueType.INCIDENT, ZeebeIncidentRecordDto.class);
    final List<ZeebeUserTaskRecordDto> userTaskRecords =
        filterAndConvert(records, ValueType.USER_TASK, ZeebeUserTaskRecordDto.class);

    // Submit all four services; the callback fires when the last non-empty service completes.
    // For simplicity, fire the callback immediately after submitting all jobs.
    if (!processInstanceRecords.isEmpty()) {
      processInstanceImportService.executeImport(processInstanceRecords, () -> {});
    }
    if (!variableRecords.isEmpty()) {
      variableImportService.executeImport(variableRecords, () -> {});
    }
    if (!incidentRecords.isEmpty()) {
      incidentImportService.executeImport(incidentRecords, () -> {});
    }
    if (!userTaskRecords.isEmpty()) {
      userTaskImportService.executeImport(userTaskRecords, () -> {});
    }
    allServicesCompleteCallback.run();
  }

  private <T> List<T> filterAndConvert(
      final List<ZeebeGenericRecordDto> records,
      final ValueType valueType,
      final Class<T> targetClass) {
    return records.stream()
        .filter(r -> valueType.equals(r.getValueType()))
        .map(r -> objectMapper.convertValue(r, targetClass))
        .toList();
  }

  private Timer getIndexingDurationTimer() {
    return OptimizeMetrics.getTimer(
        INDEXING_DURATION_METRIC, "COMBINED", fetcher.getPartitionId());
  }

  private void calculateNewBackoffUntilBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      LOG.debug(
          "Maximum idle backoff reached, this mediator will not backoff any further than {}ms.",
          idleBackoffCalculator.getMaximumBackoffMilliseconds());
    }
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    LOG.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }

  private void sleep(final long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (final InterruptedException e) {
      LOG.error("Was interrupted from sleep.", e);
      Thread.currentThread().interrupt();
    }
  }
}
