/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.search.entities.BatchOperationType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HistoryCleanupServiceTest {

  private static final int PARTITION_ID = 1;
  private static final OffsetDateTime CLEANUP_DATE = OffsetDateTime.now();

  private RdbmsWriterConfig config;
  private ProcessInstanceWriter processInstanceWriter;
  private ProcessInstanceDbReader processInstanceReader;
  private IncidentWriter incidentWriter;
  private FlowNodeInstanceWriter flowNodeInstanceWriter;
  private UserTaskWriter userTaskWriter;
  private VariableWriter variableInstanceWriter;
  private DecisionInstanceWriter decisionInstanceWriter;
  private JobWriter jobWriter;
  private SequenceFlowWriter sequenceFlowWriter;
  private BatchOperationWriter batchOperationWriter;
  private MessageSubscriptionWriter messageSubscriptionWriter;
  private CorrelatedMessageSubscriptionWriter correlatedMessageSubscriptionWriter;
  private UsageMetricWriter usageMetricWriter;
  private UsageMetricTUWriter usageMetricTUWriter;
  private AuditLogWriter auditLogWriter;

  private HistoryCleanupService historyCleanupService;

  @BeforeEach
  void setUp() {
    config = mock(RdbmsWriterConfig.class);
    processInstanceWriter = mock(ProcessInstanceWriter.class);
    processInstanceReader = mock(ProcessInstanceDbReader.class);
    incidentWriter = mock(IncidentWriter.class);
    flowNodeInstanceWriter = mock(FlowNodeInstanceWriter.class);
    userTaskWriter = mock(UserTaskWriter.class);
    variableInstanceWriter = mock(VariableWriter.class);
    decisionInstanceWriter = mock(DecisionInstanceWriter.class);
    jobWriter = mock(JobWriter.class);
    sequenceFlowWriter = mock(SequenceFlowWriter.class);
    batchOperationWriter = mock(BatchOperationWriter.class);
    messageSubscriptionWriter = mock(MessageSubscriptionWriter.class);
    correlatedMessageSubscriptionWriter = mock(CorrelatedMessageSubscriptionWriter.class);
    usageMetricWriter = mock(UsageMetricWriter.class);
    usageMetricTUWriter = mock(UsageMetricTUWriter.class);
    auditLogWriter = mock(AuditLogWriter.class);
    when(processInstanceReader.selectExpiredRootProcessInstances(anyInt(), any(), anyInt()))
        .thenReturn(java.util.Collections.emptyList());

    when(flowNodeInstanceWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(0);
    when(incidentWriter.deleteRootProcessInstanceRelatedData(any(), anyInt())).thenReturn(0);
    when(userTaskWriter.deleteRootProcessInstanceRelatedData(any(), anyInt())).thenReturn(0);
    when(variableInstanceWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(0);
    when(decisionInstanceWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(0);
    when(jobWriter.deleteRootProcessInstanceRelatedData(any(), anyInt())).thenReturn(0);
    when(sequenceFlowWriter.deleteRootProcessInstanceRelatedData(any(), anyInt())).thenReturn(0);
    when(messageSubscriptionWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(0);
    when(correlatedMessageSubscriptionWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(0);
    when(auditLogWriter.deleteRootProcessInstanceRelatedData(any(), anyInt())).thenReturn(0);

    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(0);
    when(usageMetricWriter.cleanupMetrics(anyInt(), any(), anyInt())).thenReturn(0);
    when(usageMetricTUWriter.cleanupMetrics(anyInt(), any(), anyInt())).thenReturn(0);
    when(decisionInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(auditLogWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);

    final var historyConfig = mock(RdbmsWriterConfig.HistoryConfig.class);
    when(config.history()).thenReturn(historyConfig);
    when(historyConfig.defaultHistoryTTL()).thenReturn(Duration.ofDays(90));
    when(historyConfig.batchOperationCancelProcessInstanceHistoryTTL())
        .thenReturn(Duration.ofDays(2));
    when(historyConfig.batchOperationMigrateProcessInstanceHistoryTTL())
        .thenReturn(Duration.ofDays(3));
    when(historyConfig.batchOperationModifyProcessInstanceHistoryTTL())
        .thenReturn(Duration.ofDays(4));
    when(historyConfig.batchOperationResolveIncidentHistoryTTL()).thenReturn(Duration.ofDays(5));
    when(historyConfig.minHistoryCleanupInterval()).thenReturn(Duration.ofHours(1));
    when(historyConfig.maxHistoryCleanupInterval()).thenReturn(Duration.ofDays(1));
    when(historyConfig.historyCleanupBatchSize()).thenReturn(100);
    when(historyConfig.historyCleanupProcessInstanceBatchSize()).thenReturn(100);
    when(historyConfig.usageMetricsCleanup()).thenReturn(Duration.ofDays(1));
    when(historyConfig.usageMetricsTTL()).thenReturn(Duration.ofDays(730));

    final var rdbmsWriters = mock(RdbmsWriters.class);
    when(rdbmsWriters.getProcessInstanceWriter()).thenReturn(processInstanceWriter);
    when(rdbmsWriters.getIncidentWriter()).thenReturn(incidentWriter);
    when(rdbmsWriters.getFlowNodeInstanceWriter()).thenReturn(flowNodeInstanceWriter);
    when(rdbmsWriters.getUserTaskWriter()).thenReturn(userTaskWriter);
    when(rdbmsWriters.getVariableWriter()).thenReturn(variableInstanceWriter);
    when(rdbmsWriters.getDecisionInstanceWriter()).thenReturn(decisionInstanceWriter);
    when(rdbmsWriters.getJobWriter()).thenReturn(jobWriter);
    when(rdbmsWriters.getSequenceFlowWriter()).thenReturn(sequenceFlowWriter);
    when(rdbmsWriters.getBatchOperationWriter()).thenReturn(batchOperationWriter);
    when(rdbmsWriters.getMessageSubscriptionWriter()).thenReturn(messageSubscriptionWriter);
    when(rdbmsWriters.getCorrelatedMessageSubscriptionWriter())
        .thenReturn(correlatedMessageSubscriptionWriter);
    when(rdbmsWriters.getUsageMetricWriter()).thenReturn(usageMetricWriter);
    when(rdbmsWriters.getUsageMetricTUWriter()).thenReturn(usageMetricTUWriter);
    when(rdbmsWriters.getMetrics())
        .thenReturn(mock(RdbmsWriterMetrics.class, Mockito.RETURNS_DEEP_STUBS));
    when(rdbmsWriters.getAuditLogWriter()).thenReturn(auditLogWriter);

    historyCleanupService = new HistoryCleanupService(config, rdbmsWriters, processInstanceReader);
  }

  @Test
  void testFirstCleanupHistory() {
    // given
    final List<Long> expiredProcessInstanceKeys = java.util.List.of(1L, 2L, 3L);
    when(processInstanceReader.selectExpiredRootProcessInstances(anyInt(), any(), anyInt()))
        .thenReturn(expiredProcessInstanceKeys);
    // All child delete less than batch size, which means they were all deleted
    when(flowNodeInstanceWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(2);

    when(processInstanceWriter.deleteByKeys(any())).thenReturn(3);
    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(1);
    when(decisionInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(2);
    when(auditLogWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);

    // dependent processes were also deleted
    when(processInstanceWriter.deleteChildrenByRootProcessInstances(any(), anyInt())).thenReturn(1);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofHours(1));
    verify(processInstanceReader)
        .selectExpiredRootProcessInstances(PARTITION_ID, CLEANUP_DATE, 100);
    // Each writer called once per cleanup cycle
    verify(flowNodeInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(incidentWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(userTaskWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(variableInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(decisionInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(jobWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(sequenceFlowWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(messageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(correlatedMessageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(auditLogWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    // PIs deleted since no child entities were found
    verify(processInstanceWriter)
        .deleteChildrenByRootProcessInstances(expiredProcessInstanceKeys, 100);
    verify(processInstanceWriter).deleteByKeys(expiredProcessInstanceKeys);
    verify(batchOperationWriter).cleanupHistory(CLEANUP_DATE, 100);
    verify(decisionInstanceWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(auditLogWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
  }

  @Test
  void testCleanupHistoryWithRemainingDependents() {
    // given - PIs with remaining child entities (won't be deleted in this cycle)
    final List<Long> expiredProcessInstanceKeys = java.util.List.of(1L, 2L, 3L);
    when(processInstanceReader.selectExpiredRootProcessInstances(anyInt(), any(), anyInt()))
        .thenReturn(expiredProcessInstanceKeys);
    // Some child entities might still exist as the batch size was hit
    when(flowNodeInstanceWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(10);
    when(incidentWriter.deleteRootProcessInstanceRelatedData(any(), anyInt())).thenReturn(5);
    when(variableInstanceWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(100);

    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(1);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofHours(1));
    verify(processInstanceReader)
        .selectExpiredRootProcessInstances(PARTITION_ID, CLEANUP_DATE, 100);
    // Each writer called once
    verify(flowNodeInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(incidentWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(userTaskWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(variableInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(decisionInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(jobWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(sequenceFlowWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(messageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(correlatedMessageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(auditLogWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    // PIs NOT deleted because child entities were found
    verify(processInstanceWriter, Mockito.never())
        .deleteChildrenByRootProcessInstances(any(), anyInt());
    verify(processInstanceWriter, Mockito.never()).deleteByKeys(any());
    verify(batchOperationWriter).cleanupHistory(CLEANUP_DATE, 100);
  }

  @Test
  void testCleanupHistoryWithRemainingDependentProcesses() {
    // given - PIs with remaining child entities (won't be deleted in this cycle)
    final List<Long> expiredProcessInstanceKeys = java.util.List.of(1L, 2L, 3L);
    when(processInstanceReader.selectExpiredRootProcessInstances(anyInt(), any(), anyInt()))
        .thenReturn(expiredProcessInstanceKeys);
    // All non-process child entities were deleted this time
    when(flowNodeInstanceWriter.deleteRootProcessInstanceRelatedData(any(), anyInt()))
        .thenReturn(1);

    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(1);

    // but dependent processes were not fully deleted (as batch size was hit)
    when(processInstanceWriter.deleteChildrenByRootProcessInstances(any(), anyInt()))
        .thenReturn(100);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofHours(1));
    verify(processInstanceReader)
        .selectExpiredRootProcessInstances(PARTITION_ID, CLEANUP_DATE, 100);
    // Each writer called once
    verify(flowNodeInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(incidentWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(userTaskWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(variableInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(decisionInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(jobWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(sequenceFlowWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(messageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(correlatedMessageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(auditLogWriter).deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, 100);
    verify(processInstanceWriter)
        .deleteChildrenByRootProcessInstances(expiredProcessInstanceKeys, 100);
    // Root PIs NOT deleted because dependent process instance entities were found
    verify(processInstanceWriter, Mockito.never()).deleteByKeys(any());
    verify(batchOperationWriter).cleanupHistory(CLEANUP_DATE, 100);
  }

  @Test
  void testFirstCleanupMetricsHistory() {
    // given
    when(usageMetricWriter.cleanupMetrics(anyInt(), any(), anyInt())).thenReturn(1);
    when(usageMetricTUWriter.cleanupMetrics(anyInt(), any(), anyInt())).thenReturn(1);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupUsageMetricsHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofDays(1));
    verify(usageMetricWriter)
        .cleanupMetrics(PARTITION_ID, CLEANUP_DATE.minus(Duration.ofDays(730)), 100);
    verify(usageMetricTUWriter)
        .cleanupMetrics(PARTITION_ID, CLEANUP_DATE.minus(Duration.ofDays(730)), 100);
  }

  @Test
  void testCalculateNewDurationWhenDeletedNothing() {
    // given
    final Map<String, Integer> numDeletedRecords = new java.util.HashMap<>();
    numDeletedRecords.put("processInstance", 0);
    numDeletedRecords.put("flowNodeInstance", 0);
    numDeletedRecords.put("incident", 0);
    numDeletedRecords.put("userTask", 0);
    numDeletedRecords.put("variable", 0);
    numDeletedRecords.put("decisionInstance", 0);
    numDeletedRecords.put("job", 0);
    numDeletedRecords.put("sequenceFlow", 0);
    numDeletedRecords.put("batchOperation", 0);
    numDeletedRecords.put("messageSubscription", 0);
    numDeletedRecords.put("correlatedMessageSubscription", 0);
    numDeletedRecords.put("auditLog", 0);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(8)); // assuming minCleanupInterval is 1 hour
  }

  @Test
  void testCalculateNewDurationWhenExceededBatchSize() {
    // given
    final Map<String, Integer> numDeletedRecords = new java.util.HashMap<>();
    numDeletedRecords.put("processInstance", 100);
    numDeletedRecords.put("flowNodeInstance", 100);
    numDeletedRecords.put("incident", 100);
    numDeletedRecords.put("userTask", 100);
    numDeletedRecords.put("variable", 100);
    numDeletedRecords.put("decisionInstance", 100);
    numDeletedRecords.put("job", 100);
    numDeletedRecords.put("sequenceFlow", 100);
    numDeletedRecords.put("batchOperationItem", 100);
    numDeletedRecords.put("batchOperation", 100);
    numDeletedRecords.put("messageSubscription", 100);
    numDeletedRecords.put("correlatedMessageSubscription", 100);
    numDeletedRecords.put("auditLog", 100);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(2)); // assuming minCleanupInterval is 1 hour
  }

  @Test
  void testCalculateNewDurationWhenNormalCleanup() {
    // given
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("processInstance", 50);
    numDeletedRecords.put("flowNodeInstance", 50);
    numDeletedRecords.put("incident", 50);
    numDeletedRecords.put("userTask", 50);
    numDeletedRecords.put("variable", 50);
    numDeletedRecords.put("decisionInstance", 50);
    numDeletedRecords.put("job", 50);
    numDeletedRecords.put("sequenceFlow", 50);
    numDeletedRecords.put("batchOperation", 50);
    numDeletedRecords.put("messageSubscription", 50);
    numDeletedRecords.put("correlatedMessageSubscription", 50);
    numDeletedRecords.put("auditLog", 50);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(4)); // assuming minCleanupInterval is 1 hour
  }

  @Test
  void testCalculateNewDurationWhenLowCleanup() {
    // given
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("processInstance", 20);
    numDeletedRecords.put("flowNodeInstance", 20);
    numDeletedRecords.put("incident", 20);
    numDeletedRecords.put("userTask", 20);
    numDeletedRecords.put("variable", 20);
    numDeletedRecords.put("decisionInstance", 20);
    numDeletedRecords.put("job", 20);
    numDeletedRecords.put("sequenceFlow", 20);
    numDeletedRecords.put("batchOperation", 20);
    numDeletedRecords.put("messageSubscription", 20);
    numDeletedRecords.put("correlatedMessageSubscription", 20);
    numDeletedRecords.put("auditLog", 20);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(8)); // assuming minCleanupInterval is 1 hour
  }

  @Test
  void testResolveBatchOperationTTL() {
    assertThat(
            historyCleanupService.resolveBatchOperationTTL(
                BatchOperationType.CANCEL_PROCESS_INSTANCE))
        .isEqualTo(Duration.ofDays(2));
    assertThat(
            historyCleanupService.resolveBatchOperationTTL(
                BatchOperationType.MIGRATE_PROCESS_INSTANCE))
        .isEqualTo(Duration.ofDays(3));
    assertThat(
            historyCleanupService.resolveBatchOperationTTL(
                BatchOperationType.MODIFY_PROCESS_INSTANCE))
        .isEqualTo(Duration.ofDays(4));
    assertThat(historyCleanupService.resolveBatchOperationTTL(BatchOperationType.RESOLVE_INCIDENT))
        .isEqualTo(Duration.ofDays(5));

    assertThat(historyCleanupService.resolveBatchOperationTTL(BatchOperationType.ADD_VARIABLE))
        .isEqualTo(Duration.ofDays(90)); // default history TTL
  }

  @Test
  void testCleanupStandaloneDecisionInstances() {
    // given - no expired root process instances, but standalone decision instances exist
    when(processInstanceReader.selectExpiredRootProcessInstances(anyInt(), any(), anyInt()))
        .thenReturn(java.util.Collections.emptyList());
    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(0);
    when(decisionInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(5);
    when(auditLogWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(3);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofHours(1));
    verify(decisionInstanceWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(auditLogWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
  }

  @Test
  void testCalculateNewDurationWhenStandaloneDecisionsHitLimit() {
    // given - standalone decision instances hit the cleanup batch size limit
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("processInstance", 0);
    numDeletedRecords.put("standaloneDecisionInstance", 100); // hit the limit
    numDeletedRecords.put("standaloneDecisionAuditLog", 50);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then - should halve the interval because standalone decisions hit limit
    assertThat(nextDuration).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void testCalculateNewDurationWhenStandaloneAuditLogsHitLimit() {
    // given - standalone audit logs hit the cleanup batch size limit
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("processInstance", 0);
    numDeletedRecords.put("standaloneDecisionInstance", 50);
    numDeletedRecords.put("standaloneDecisionAuditLog", 100); // hit the limit

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then - should halve the interval because audit logs hit limit
    assertThat(nextDuration).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void testCalculateNewDurationWithStandaloneRecordsButNoLimit() {
    // given - some standalone records deleted but not hitting limit
    // processInstance: 10 (< 50 threshold for PI batch size 100)
    // max(standaloneDecision=20, auditLog=15) = 20 (< 50 threshold for cleanup batch size 100)
    // Both are below their respective thresholds, so interval should be doubled
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("processInstance", 10);
    numDeletedRecords.put("standaloneDecisionInstance", 20);
    numDeletedRecords.put("standaloneDecisionAuditLog", 15);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then - should double the interval (both PIs and other entities below half threshold)
    assertThat(nextDuration).isEqualTo(Duration.ofHours(8));
  }

  @Test
  void testCalculateNewDurationWithHighProcessInstancesOnly() {
    // given - process instances above threshold but other entities below
    // processInstance: 60 (>= 50 threshold but < 100 limit)
    // max(standaloneDecision=10, auditLog=5) = 10 (< 50 threshold)
    // PIs are above threshold, so interval should remain unchanged
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("processInstance", 60);
    numDeletedRecords.put("standaloneDecisionInstance", 10);
    numDeletedRecords.put("standaloneDecisionAuditLog", 5);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then - should keep interval unchanged (PIs above half threshold but not at limit)
    assertThat(nextDuration).isEqualTo(Duration.ofHours(4));
  }

  @Test
  void testCalculateNewDurationWithHighOtherEntitiesOnly() {
    // given - other entities above threshold but PIs below
    // processInstance: 10 (< 50 threshold)
    // max(standaloneDecision=60, auditLog=30) = 60 (>= 50 threshold but < 100 limit)
    // Other entities are above threshold, so interval should remain unchanged
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("processInstance", 10);
    numDeletedRecords.put("standaloneDecisionInstance", 60);
    numDeletedRecords.put("standaloneDecisionAuditLog", 30);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then - should keep interval unchanged (other entities above half threshold but not at limit)
    assertThat(nextDuration).isEqualTo(Duration.ofHours(4));
  }
}
