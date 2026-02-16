/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryCleanupService {

  private static final Logger LOG = LoggerFactory.getLogger(HistoryCleanupService.class);

  private final Duration defaultHistoryTTL;
  private final Duration batchOperationCancelProcessInstanceHistoryTTL;
  private final Duration batchOperationMigrateProcessInstanceHistoryTTL;
  private final Duration batchOperationModifyProcessInstanceHistoryTTL;
  private final Duration batchOperationResolveIncidentHistoryTTL;
  private final Duration minCleanupInterval;
  private final Duration maxCleanupInterval;
  private final int cleanupBatchSize;
  private final int processInstanceBatchSize;
  private final Duration usageMetricsCleanup;
  private final Duration usageMetricsTTL;

  private final RdbmsWriterMetrics metrics;

  private final ProcessInstanceWriter processInstanceWriter;
  private final ProcessInstanceDbReader processInstanceReader;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final BatchOperationWriter batchOperationWriter;
  private final UsageMetricWriter usageMetricWriter;
  private final UsageMetricTUWriter usageMetricTUWriter;
  private final AuditLogWriter auditLogWriter;
  private final Map<String, ProcessInstanceDependant> rootProcessInstanceDependentChildWriters;

  private final Map<Integer, Duration> lastCleanupInterval = new HashMap<>();

  public HistoryCleanupService(
      final RdbmsWriterConfig config,
      final RdbmsWriters rdbmsWriters,
      final ProcessInstanceDbReader processInstanceReader) {
    LOG.info(
        "Creating HistoryCleanupService with default history ttl {}",
        config.history().defaultHistoryTTL());

    defaultHistoryTTL = config.history().defaultHistoryTTL();
    batchOperationCancelProcessInstanceHistoryTTL =
        config.history().batchOperationCancelProcessInstanceHistoryTTL();
    batchOperationMigrateProcessInstanceHistoryTTL =
        config.history().batchOperationMigrateProcessInstanceHistoryTTL();
    batchOperationModifyProcessInstanceHistoryTTL =
        config.history().batchOperationModifyProcessInstanceHistoryTTL();
    batchOperationResolveIncidentHistoryTTL =
        config.history().batchOperationResolveIncidentHistoryTTL();
    minCleanupInterval = config.history().minHistoryCleanupInterval();
    maxCleanupInterval = config.history().maxHistoryCleanupInterval();
    usageMetricsCleanup = config.history().usageMetricsCleanup();
    usageMetricsTTL = config.history().usageMetricsTTL();
    cleanupBatchSize = config.history().historyCleanupBatchSize();
    processInstanceBatchSize = config.history().historyCleanupProcessInstanceBatchSize();
    processInstanceWriter = rdbmsWriters.getProcessInstanceWriter();
    this.processInstanceReader = processInstanceReader;
    metrics = rdbmsWriters.getMetrics();
    decisionInstanceWriter = rdbmsWriters.getDecisionInstanceWriter();
    batchOperationWriter = rdbmsWriters.getBatchOperationWriter();
    usageMetricWriter = rdbmsWriters.getUsageMetricWriter();
    usageMetricTUWriter = rdbmsWriters.getUsageMetricTUWriter();
    auditLogWriter = rdbmsWriters.getAuditLogWriter();

    rootProcessInstanceDependentChildWriters =
        Map.ofEntries(
            Map.entry("flowNodeInstance", rdbmsWriters.getFlowNodeInstanceWriter()),
            Map.entry("incident", rdbmsWriters.getIncidentWriter()),
            Map.entry("userTask", rdbmsWriters.getUserTaskWriter()),
            Map.entry("variable", rdbmsWriters.getVariableWriter()),
            Map.entry("decisionInstance", rdbmsWriters.getDecisionInstanceWriter()),
            Map.entry("job", rdbmsWriters.getJobWriter()),
            Map.entry("sequenceFlow", rdbmsWriters.getSequenceFlowWriter()),
            Map.entry("messageSubscription", rdbmsWriters.getMessageSubscriptionWriter()),
            Map.entry(
                "correlatedMessageSubscription",
                rdbmsWriters.getCorrelatedMessageSubscriptionWriter()),
            Map.entry("auditLog", rdbmsWriters.getAuditLogWriter()));
  }

  public void scheduleProcessForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime endDate) {
    final OffsetDateTime historyCleanupDate = endDate.plus(defaultHistoryTTL);

    LOG.trace(
        "Scheduling process instance cleanup for key {} at {}",
        processInstanceKey,
        historyCleanupDate);
    // Only update the process instance record itself (single row update) if it is a root process
    // instance
    // Child entities will be deleted by the cleanup job using the PI's cleanup date
    processInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
  }

  public void scheduleBatchOperationForHistoryCleanup(
      final String batchOperationKey,
      final BatchOperationType batchOperationType,
      final OffsetDateTime endDate) {

    final var ttl = resolveBatchOperationTTL(batchOperationType);
    final var historyCleanupDate = endDate.plus(ttl);

    LOG.trace(
        "Scheduling batch operation cleanup for key {} at {}",
        batchOperationKey,
        historyCleanupDate);
    batchOperationWriter.scheduleForHistoryCleanup(batchOperationKey, historyCleanupDate);
  }

  @VisibleForTesting
  public Duration resolveBatchOperationTTL(final BatchOperationType type) {
    return switch (type) {
      case CANCEL_PROCESS_INSTANCE -> batchOperationCancelProcessInstanceHistoryTTL;
      case MIGRATE_PROCESS_INSTANCE -> batchOperationMigrateProcessInstanceHistoryTTL;
      case MODIFY_PROCESS_INSTANCE -> batchOperationModifyProcessInstanceHistoryTTL;
      case RESOLVE_INCIDENT -> batchOperationResolveIncidentHistoryTTL;
      default -> defaultHistoryTTL;
    };
  }

  public Duration cleanupHistory(final int partitionId, final OffsetDateTime cleanupDate) {
    LOG.trace("Cleanup history for partition {} with TTL before {}", partitionId, cleanupDate);

    try (final var sample = metrics.measureHistoryCleanupDuration()) {
      final long start = System.currentTimeMillis();

      final var numDeletedRecords = new HashMap<String, Integer>();

      // Query expired process instances (bounded by processInstanceBatchSize)
      // This is kept small to avoid Oracle IN-clause limit (1000) when passing
      // PI keys to deleteRootProcessInstanceRelatedData()
      final List<Long> expiredRootProcessInstanceKeys =
          processInstanceReader.selectExpiredRootProcessInstances(
              partitionId, cleanupDate, processInstanceBatchSize);

      if (!expiredRootProcessInstanceKeys.isEmpty()) {
        LOG.debug(
            "Found {} expired root process instances for cleanup on partition {}",
            expiredRootProcessInstanceKeys.size(),
            partitionId);

        // Delete up to batchSize child entities for all expired PIs
        // If any child entities remain, the PI won't be deleted and will be retried in next cycle
        // This keeps each cleanup cycle bounded and simple
        int totalChildEntitiesDeleted = 0;
        boolean anyDeletionHitBatchLimit = false;
        for (final var entry : rootProcessInstanceDependentChildWriters.entrySet()) {
          final var writer = entry.getValue();
          final var numDeleted =
              writer.deleteRootProcessInstanceRelatedData(
                  expiredRootProcessInstanceKeys, cleanupBatchSize);
          totalChildEntitiesDeleted += numDeleted;
          anyDeletionHitBatchLimit = anyDeletionHitBatchLimit || (numDeleted >= cleanupBatchSize);
          numDeletedRecords.put(entry.getKey(), numDeleted);
        }

        // Only delete PIs if we're confident all children are gone:
        // as long as no entity deletion hit the batch limit, we can be sure all children are gone
        int deletedChildPIs = 0;
        if (!anyDeletionHitBatchLimit) {
          deletedChildPIs =
              processInstanceWriter.deleteChildrenByRootProcessInstances(
                  expiredRootProcessInstanceKeys, cleanupBatchSize);
          totalChildEntitiesDeleted += deletedChildPIs;
          LOG.debug(
              "Deleted {} child process instances with no remaining dependents on partition {}",
              deletedChildPIs,
              partitionId);
          numDeletedRecords.put("childProcessInstance", deletedChildPIs);
        }
        // Only delete Root PIs if we're confident all related PIs and their children are gone
        if (!anyDeletionHitBatchLimit && deletedChildPIs < cleanupBatchSize) {
          final int deletedRPIs =
              processInstanceWriter.deleteByKeys(expiredRootProcessInstanceKeys);
          numDeletedRecords.put("rootProcessInstance", deletedRPIs);
          LOG.debug(
              "Deleted {} root process instances with no remaining dependents on partition {}",
              deletedRPIs,
              partitionId);
        } else {
          LOG.debug(
              "Deleted {} child entities for {} root process instances on partition {}. "
                  + "Root process instances will be retried in next cleanup cycle.",
              totalChildEntitiesDeleted,
              expiredRootProcessInstanceKeys.size(),
              partitionId);
        }
      }

      // Keep existing logic for batch operations (no change needed)
      numDeletedRecords.put(
          "batchOperation", batchOperationWriter.cleanupHistory(cleanupDate, cleanupBatchSize));

      // Clean up standalone decision instances (without process instance context)
      numDeletedRecords.put(
          "standaloneDecisionInstance",
          decisionInstanceWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));

      // Clean up audit logs for standalone decision instances
      numDeletedRecords.put(
          "standaloneDecisionAuditLog",
          auditLogWriter.cleanupHistory(partitionId, cleanupDate, cleanupBatchSize));

      final long end = System.currentTimeMillis();
      logCleanUpInfo("", partitionId, numDeletedRecords, cleanupDate, end, start);

      final var nextDuration =
          calculateNewDuration(lastCleanupInterval.get(partitionId), numDeletedRecords);
      LOG.trace("Schedule next cleanup for partition {} with TTL in {}", partitionId, nextDuration);

      saveLastCleanupInterval(partitionId, nextDuration);
      return nextDuration;
    }
  }

  public Duration cleanupUsageMetricsHistory(final int partitionId, final OffsetDateTime now) {

    final var cleanupDate = now.minus(usageMetricsTTL);
    LOG.trace(
        "Cleanup usage metrics history for partition {} with date before {}",
        partitionId,
        cleanupDate);

    try (final var sample = metrics.measureUsageMetricsHistoryCleanupDuration()) {
      final long start = System.currentTimeMillis();

      final var numDeletedRecords = new HashMap<String, Integer>();
      numDeletedRecords.put(
          "usageMetrics",
          usageMetricWriter.cleanupMetrics(partitionId, cleanupDate, cleanupBatchSize));
      numDeletedRecords.put(
          "usageMetricsTU",
          usageMetricTUWriter.cleanupMetrics(partitionId, cleanupDate, cleanupBatchSize));

      final long end = System.currentTimeMillis();

      logCleanUpInfo("Usage Metrics", partitionId, numDeletedRecords, cleanupDate, end, start);
    }

    return usageMetricsCleanup;
  }

  private void logCleanUpInfo(
      final String cleanupType,
      final int partitionId,
      final HashMap<String, Integer> numDeletedRecords,
      final OffsetDateTime cleanupDate,
      final long end,
      final long start) {
    final int sum = numDeletedRecords.values().stream().mapToInt(Integer::intValue).sum();

    LOG.debug("Deleted {}history records: {}", cleanupType, numDeletedRecords);
    for (final var entry : numDeletedRecords.entrySet()) {
      LOG.debug("    Deleted {}s: {}", entry.getKey(), entry.getValue());
      metrics.recordHistoryCleanupEntities(entry.getValue(), entry.getKey());
    }

    LOG.debug(
        "{}Cleanup history for partition {} with TTL before {} took {} ms. Deleted {} records",
        cleanupType,
        partitionId,
        cleanupDate,
        end - start,
        sum);
  }

  private void saveLastCleanupInterval(final int partitionId, final Duration nextDuration) {
    if (lastCleanupInterval.put(partitionId, nextDuration) == null) {
      metrics.registerCleanupBackoffDurationGauge(
          () -> lastCleanupInterval.get(partitionId).toMillis());
    }
  }

  /**
   * Calculates the next cleanup interval duration based on the number of deleted records and the
   * previous interval. The interval is adjusted as follows:
   *
   * <ul>
   *   <li>If this is the first run ({@code lastDuration} is {@code null}), use {@code
   *       minCleanupInterval}.
   *   <li>If deleted process instances are fewer than half of {@code processInstanceBatchSize} AND
   *       other entities (standalone decisions, audit logs, batch operations) are fewer than half
   *       of {@code cleanupBatchSize}, double the interval, but do not exceed {@code
   *       maxCleanupInterval}.
   *   <li>If process instances hit or exceed {@code processInstanceBatchSize} OR any other entity
   *       type hits or exceeds {@code cleanupBatchSize}, halve the interval, but do not go below
   *       {@code minCleanupInterval}.
   *   <li>Otherwise, keep the interval unchanged.
   * </ul>
   *
   * @param lastDuration the previous cleanup interval duration, or {@code null} if first run
   * @param numDeletedRecords a map containing the number of deleted records by entity type
   * @return the next cleanup interval duration
   */
  @VisibleForTesting
  Duration calculateNewDuration(
      final Duration lastDuration, final Map<String, Integer> numDeletedRecords) {
    final int deletedProcessInstances = numDeletedRecords.getOrDefault("processInstance", 0);
    final int deletedStandaloneDecisions =
        numDeletedRecords.getOrDefault("standaloneDecisionInstance", 0);
    final int deletedStandaloneAuditLogs =
        numDeletedRecords.getOrDefault("standaloneDecisionAuditLog", 0);
    final int deletedBatchOperations = numDeletedRecords.getOrDefault("batchOperation", 0);

    // Check if process instances hit their batch size limit
    final boolean processInstancesHitLimit = deletedProcessInstances >= processInstanceBatchSize;

    // Check if any other entity type hit the cleanup batch size limit
    final int maxOtherEntityDeleted =
        Math.max(
            deletedStandaloneDecisions,
            Math.max(deletedStandaloneAuditLogs, deletedBatchOperations));
    final boolean otherEntitiesHitLimit = maxOtherEntityDeleted >= cleanupBatchSize;

    Duration nextDuration;

    if (lastDuration == null) {
      nextDuration = minCleanupInterval;
    } else if (deletedProcessInstances < processInstanceBatchSize / 2
        && maxOtherEntityDeleted < cleanupBatchSize / 2) {
      // Both process instances and other entities are below half their respective thresholds
      nextDuration = lastDuration.multipliedBy(2);
      nextDuration =
          nextDuration.compareTo(maxCleanupInterval) < 0 ? nextDuration : maxCleanupInterval;
    } else if (processInstancesHitLimit || otherEntitiesHitLimit) {
      // Either process instances or other entities hit their respective limits
      nextDuration = lastDuration.dividedBy(2);
      nextDuration =
          nextDuration.compareTo(minCleanupInterval) > 0 ? nextDuration : minCleanupInterval;
    } else {
      nextDuration = lastDuration;
    }

    return nextDuration;
  }

  @VisibleForTesting
  public Duration getHistoryCleanupInterval() {
    return defaultHistoryTTL;
  }

  public Duration getCurrentCleanupInterval(final int partitionId) {
    return lastCleanupInterval.getOrDefault(partitionId, minCleanupInterval);
  }

  public Duration getUsageMetricsHistoryCleanupInterval() {
    return usageMetricsCleanup;
  }
}
