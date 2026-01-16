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
  private final IncidentWriter incidentWriter;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final UserTaskWriter userTaskWriter;
  private final VariableWriter variableInstanceWriter;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final JobWriter jobWriter;
  private final SequenceFlowWriter sequenceFlowWriter;
  private final BatchOperationWriter batchOperationWriter;
  private final MessageSubscriptionWriter messageSubscriptionWriter;
  private final CorrelatedMessageSubscriptionWriter correlatedMessageSubscriptionWriter;
  private final UsageMetricWriter usageMetricWriter;
  private final UsageMetricTUWriter usageMetricTUWriter;
  private final AuditLogWriter auditLogWriter;

  private final Map<Integer, Duration> lastCleanupInterval = new HashMap<>();

  public HistoryCleanupService(final RdbmsWriterConfig config, final RdbmsWriters rdbmsWriters) {
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
    incidentWriter = rdbmsWriters.getIncidentWriter();
    flowNodeInstanceWriter = rdbmsWriters.getFlowNodeInstanceWriter();
    userTaskWriter = rdbmsWriters.getUserTaskWriter();
    variableInstanceWriter = rdbmsWriters.getVariableWriter();
    decisionInstanceWriter = rdbmsWriters.getDecisionInstanceWriter();
    jobWriter = rdbmsWriters.getJobWriter();
    sequenceFlowWriter = rdbmsWriters.getSequenceFlowWriter();
    batchOperationWriter = rdbmsWriters.getBatchOperationWriter();
    messageSubscriptionWriter = rdbmsWriters.getMessageSubscriptionWriter();
    correlatedMessageSubscriptionWriter = rdbmsWriters.getCorrelatedMessageSubscriptionWriter();
    metrics = rdbmsWriters.getMetrics();
    usageMetricWriter = rdbmsWriters.getUsageMetricWriter();
    usageMetricTUWriter = rdbmsWriters.getUsageMetricTUWriter();
    auditLogWriter = rdbmsWriters.getAuditLogWriter();
  }

  public void scheduleProcessForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime endDate) {
    final OffsetDateTime historyCleanupDate = endDate.plus(defaultHistoryTTL);

    LOG.trace(
        "Scheduling process instance cleanup for key {} at {}",
        processInstanceKey,
        historyCleanupDate);
    // Only update the process instance record itself (single row update)
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
      // PI keys to deleteProcessInstanceRelatedData()
      final List<Long> expiredProcessInstanceKeys =
          processInstanceWriter.selectExpiredProcessInstances(
              partitionId, cleanupDate, processInstanceBatchSize);

      if (!expiredProcessInstanceKeys.isEmpty()) {
        LOG.debug(
            "Found {} expired process instances for cleanup on partition {}",
            expiredProcessInstanceKeys.size(),
            partitionId);

        // Delete up to batchSize child entities for all expired PIs
        // If any child entities remain, the PI won't be deleted and will be retried in next cycle
        // This keeps each cleanup cycle bounded and simple
        final int deletedFlowNodes =
            flowNodeInstanceWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("flowNodeInstance", deletedFlowNodes, Integer::sum);

        final int deletedIncidents =
            incidentWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("incident", deletedIncidents, Integer::sum);

        final int deletedUserTasks =
            userTaskWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("userTask", deletedUserTasks, Integer::sum);

        final int deletedVariables =
            variableInstanceWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("variable", deletedVariables, Integer::sum);

        final int deletedDecisions =
            decisionInstanceWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("decisionInstance", deletedDecisions, Integer::sum);

        final int deletedJobs =
            jobWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("job", deletedJobs, Integer::sum);

        final int deletedSequenceFlows =
            sequenceFlowWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("sequenceFlow", deletedSequenceFlows, Integer::sum);

        final int deletedMessageSubs =
            messageSubscriptionWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("messageSubscription", deletedMessageSubs, Integer::sum);

        final int deletedCorrelatedMessageSubs =
            correlatedMessageSubscriptionWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge(
            "correlatedMessageSubscription", deletedCorrelatedMessageSubs, Integer::sum);

        final int deletedAuditLogs =
            auditLogWriter.deleteProcessInstanceRelatedData(
                expiredProcessInstanceKeys, cleanupBatchSize);
        numDeletedRecords.merge("auditLog", deletedAuditLogs, Integer::sum);

        // Calculate total child entities deleted in THIS batch
        final int totalChildEntitiesDeleted =
            deletedFlowNodes
                + deletedIncidents
                + deletedUserTasks
                + deletedVariables
                + deletedDecisions
                + deletedJobs
                + deletedSequenceFlows
                + deletedMessageSubs
                + deletedCorrelatedMessageSubs
                + deletedAuditLogs;

        // Only delete PIs if NO child entities were deleted (meaning all are already gone)
        // If child entities were deleted, PIs will be retried in the next cleanup cycle
        if (totalChildEntitiesDeleted == 0) {
          final int deletedPIs = processInstanceWriter.deleteByKeys(expiredProcessInstanceKeys);
          numDeletedRecords.merge("processInstance", deletedPIs, Integer::sum);
          LOG.debug(
              "Deleted {} process instances with no remaining dependents on partition {}",
              deletedPIs,
              partitionId);
        } else {
          LOG.debug(
              "Deleted {} child entities for {} process instances on partition {}. "
                  + "Process instances will be retried in next cleanup cycle.",
              totalChildEntitiesDeleted,
              expiredProcessInstanceKeys.size(),
              partitionId);
        }
      }

      // Keep existing logic for batch operations (no change needed)
      numDeletedRecords.put(
          "batchOperationItem",
          batchOperationWriter.cleanupItemHistory(cleanupDate, cleanupBatchSize));
      numDeletedRecords.put(
          "batchOperation", batchOperationWriter.cleanupHistory(cleanupDate, cleanupBatchSize));

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

  @VisibleForTesting
  Duration calculateNewDuration(
      final Duration lastDuration, final Map<String, Integer> numDeletedRecords) {
    final var deletedLessThanHalf =
        numDeletedRecords.values().stream().allMatch(i -> i < cleanupBatchSize / 2);
    final var exceededBatchSize =
        numDeletedRecords.values().stream().anyMatch(i -> i >= cleanupBatchSize);
    Duration nextDuration;

    if (lastDuration == null) {
      nextDuration = minCleanupInterval;
    } else if (deletedLessThanHalf) {
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

  @VisibleForTesting
  public Duration getHistoryCleanupInterval() {
    return defaultHistoryTTL;
  }
}
