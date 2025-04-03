/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryCleanupService {

  private static final Logger LOG = LoggerFactory.getLogger(HistoryCleanupService.class);

  private final Duration defaultHistoryTTL;
  private final Duration minCleanupInterval;
  private final Duration maxCleanupInterval;
  private final int cleanupBatchSize;

  private final RdbmsWriterMetrics metrics;

  private final ProcessInstanceWriter processInstanceWriter;
  private final IncidentWriter incidentWriter;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final UserTaskWriter userTaskWriter;
  private final VariableWriter variableInstanceWriter;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final JobWriter jobWriter;

  private final Map<Integer, Duration> lastCleanupInterval = new HashMap<>();

  public HistoryCleanupService(
      final RdbmsWriterConfig config,
      final ProcessInstanceWriter processInstanceWriter,
      final IncidentWriter incidentWriter,
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final UserTaskWriter userTaskWriter,
      final VariableWriter variableInstanceWriter,
      final DecisionInstanceWriter decisionInstanceWriter,
      final JobWriter jobWriter,
      final RdbmsWriterMetrics metrics) {
    LOG.info(
        "Creating HistoryCleanupService with default history ttl {}", config.defaultHistoryTTL());

    defaultHistoryTTL = config.defaultHistoryTTL();
    minCleanupInterval = config.minHistoryCleanupInterval();
    maxCleanupInterval = config.maxHistoryCleanupInterval();
    cleanupBatchSize = config.historyCleanupBatchSize();
    this.processInstanceWriter = processInstanceWriter;
    this.incidentWriter = incidentWriter;
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.userTaskWriter = userTaskWriter;
    this.variableInstanceWriter = variableInstanceWriter;
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.jobWriter = jobWriter;
    this.metrics = metrics;
  }

  public void scheduleProcessForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime endDate) {
    final OffsetDateTime historyCleanupDate = endDate.plus(defaultHistoryTTL);

    LOG.debug(
        "Scheduling process instance cleanup for key {} at {}",
        processInstanceKey,
        historyCleanupDate);
    processInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    flowNodeInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    incidentWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    userTaskWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    variableInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    decisionInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    jobWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
  }

  public Duration cleanupHistory(final int partitionId, final OffsetDateTime cleanupDate) {
    LOG.debug("Cleanup history for partition {} with TTL before {}", partitionId, cleanupDate);

    final var sample = metrics.measureHistoryCleanupDuration();
    final long start = System.currentTimeMillis();

    final var numDeletedRecords = new HashMap<String, Integer>();
    numDeletedRecords.put(
        "processInstance",
        processInstanceWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));
    numDeletedRecords.put(
        "flowNodeInstance",
        flowNodeInstanceWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));
    numDeletedRecords.put(
        "incident", incidentWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));
    numDeletedRecords.put(
        "userTask", userTaskWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));
    numDeletedRecords.put(
        "variable",
        variableInstanceWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));
    numDeletedRecords.put(
        "decisionInstance",
        decisionInstanceWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));
    numDeletedRecords.put(
        "job", jobWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));
    final long end = System.currentTimeMillis();
    sample.close();

    final int sum = numDeletedRecords.values().stream().mapToInt(Integer::intValue).sum();

    LOG.debug("Deleted history records: {}", numDeletedRecords);
    for (final var entry : numDeletedRecords.entrySet()) {
      LOG.debug("    Deleted {}s: {}", entry.getKey(), entry.getValue());
    }

    LOG.info(
        "Cleanup history for partition {} with TTL before {} took {} ms. Deleted {} records",
        partitionId,
        cleanupDate,
        end - start,
        sum);

    final var nextDuration =
        calculateNewDuration(lastCleanupInterval.get(partitionId), numDeletedRecords);
    LOG.debug("Schedule next cleanup for partition {} with TTL in {}", partitionId, nextDuration);

    saveLastCleanupInterval(partitionId, nextDuration);
    return nextDuration;
  }

  private void saveLastCleanupInterval(final int partitionId, final Duration nextDuration) {
    if (lastCleanupInterval.put(partitionId, nextDuration) == null) {
      metrics.registerCleanupBackoffDurationGauge(
          partitionId, () -> lastCleanupInterval.get(partitionId).toMillis());
    }
  }

  @VisibleForTesting
  Duration calculateNewDuration(
      final Duration lastDuration, final Map<String, Integer> numDeletedRecords) {
    final var deletedNothing = numDeletedRecords.values().stream().allMatch(i -> i == 0);
    final var exceededBatchSize =
        numDeletedRecords.values().stream().anyMatch(i -> i >= cleanupBatchSize);
    Duration nextDuration;

    if (lastDuration == null) {
      nextDuration = minCleanupInterval;
    } else if (deletedNothing) {
      nextDuration = lastDuration.multipliedBy(2);
      nextDuration =
          nextDuration.compareTo(maxCleanupInterval) < 0 ? nextDuration : maxCleanupInterval;
    } else if (exceededBatchSize) {
      nextDuration = lastDuration.dividedBy(2);
      nextDuration =
          nextDuration.compareTo(minCleanupInterval) > 0 ? nextDuration : minCleanupInterval;
    } else {
      nextDuration = lastDuration;
    }

    return nextDuration;
  }

  public Duration getHistoryCleanupInterval() {
    return defaultHistoryTTL;
  }
}
