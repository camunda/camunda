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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class HistoryCleanupServiceTest {

  private static final int PARTITION_ID = 1;
  private static final OffsetDateTime CLEANUP_DATE = OffsetDateTime.now();
  private static final int PROCESS_INSTANCE_BATCH_SIZE = 100;
  private static final int CHILD_ENTITY_BATCH_SIZE = 1000;

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

  private RdbmsWriterConfig.HistoryConfig historyConfig;
  private RdbmsWriters rdbmsWriters;

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

    historyConfig = mock(RdbmsWriterConfig.HistoryConfig.class);
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
    when(historyConfig.historyCleanupBatchSize()).thenReturn(CHILD_ENTITY_BATCH_SIZE);
    when(historyConfig.historyCleanupProcessInstanceBatchSize())
        .thenReturn(PROCESS_INSTANCE_BATCH_SIZE);
    when(historyConfig.usageMetricsCleanup()).thenReturn(Duration.ofDays(1));
    when(historyConfig.usageMetricsTTL()).thenReturn(Duration.ofDays(730));
    when(historyConfig.maxHistoryCleanupUsage())
        .thenReturn(RdbmsWriterConfig.HistoryConfig.DEFAULT_MAX_HISTORY_CLEANUP_USAGE);

    rdbmsWriters = mock(RdbmsWriters.class);
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
        .selectExpiredRootProcessInstances(PARTITION_ID, CLEANUP_DATE, PROCESS_INSTANCE_BATCH_SIZE);
    // Each writer called once per cleanup cycle
    verify(flowNodeInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(incidentWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(userTaskWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(variableInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(decisionInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(jobWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(sequenceFlowWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(messageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(correlatedMessageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(auditLogWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    // PIs deleted since no child entities were found
    verify(processInstanceWriter)
        .deleteChildrenByRootProcessInstances(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(processInstanceWriter).deleteByKeys(expiredProcessInstanceKeys);
    verify(batchOperationWriter).cleanupHistory(CLEANUP_DATE, CHILD_ENTITY_BATCH_SIZE);
    verify(decisionInstanceWriter)
        .cleanupHistory(PARTITION_ID, CLEANUP_DATE, CHILD_ENTITY_BATCH_SIZE);
    verify(auditLogWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, CHILD_ENTITY_BATCH_SIZE);
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
        .thenReturn(CHILD_ENTITY_BATCH_SIZE);

    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(1);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofHours(1));
    verify(processInstanceReader)
        .selectExpiredRootProcessInstances(PARTITION_ID, CLEANUP_DATE, PROCESS_INSTANCE_BATCH_SIZE);
    // Each writer called once
    verify(flowNodeInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(incidentWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(userTaskWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(variableInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(decisionInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(jobWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(sequenceFlowWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(messageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(correlatedMessageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(auditLogWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    // PIs NOT deleted because child entities were found
    verify(processInstanceWriter, Mockito.never())
        .deleteChildrenByRootProcessInstances(any(), anyInt());
    verify(processInstanceWriter, Mockito.never()).deleteByKeys(any());
    verify(batchOperationWriter).cleanupHistory(CLEANUP_DATE, CHILD_ENTITY_BATCH_SIZE);
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
        .thenReturn(CHILD_ENTITY_BATCH_SIZE);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofHours(1));
    verify(processInstanceReader)
        .selectExpiredRootProcessInstances(PARTITION_ID, CLEANUP_DATE, PROCESS_INSTANCE_BATCH_SIZE);
    // Each writer called once
    verify(flowNodeInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(incidentWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(userTaskWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(variableInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(decisionInstanceWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(jobWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(sequenceFlowWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(messageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(correlatedMessageSubscriptionWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(auditLogWriter)
        .deleteRootProcessInstanceRelatedData(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    verify(processInstanceWriter)
        .deleteChildrenByRootProcessInstances(expiredProcessInstanceKeys, CHILD_ENTITY_BATCH_SIZE);
    // Root PIs NOT deleted because dependent process instance entities were found
    verify(processInstanceWriter, Mockito.never()).deleteByKeys(any());
    verify(batchOperationWriter).cleanupHistory(CLEANUP_DATE, CHILD_ENTITY_BATCH_SIZE);
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
        .cleanupMetrics(
            PARTITION_ID, CLEANUP_DATE.minus(Duration.ofDays(730)), CHILD_ENTITY_BATCH_SIZE);
    verify(usageMetricTUWriter)
        .cleanupMetrics(
            PARTITION_ID, CLEANUP_DATE.minus(Duration.ofDays(730)), CHILD_ENTITY_BATCH_SIZE);
  }

  @Test
  void testCalculateNewDurationWhenDeletedNothing() {
    // given
    final Map<String, Integer> numDeletedRecords = new java.util.HashMap<>();
    numDeletedRecords.put("rootProcessInstance", 0);
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
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(8)); // assuming minCleanupInterval is 1 hour
  }

  @Test
  void testCalculateNewDurationWhenRootPIsExceededBatchSize() {
    // given
    final Map<String, Integer> numDeletedRecords = new java.util.HashMap<>();
    numDeletedRecords.put("rootProcessInstance", PROCESS_INSTANCE_BATCH_SIZE);

    numDeletedRecords.put("auditLog", PROCESS_INSTANCE_BATCH_SIZE);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(2)); // assuming minCleanupInterval is 1 hour
  }

  @Test
  void testCalculateNewDurationWhenNormalCleanup() {
    // given
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", 50);

    numDeletedRecords.put("auditLog", 50);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(4)); // assuming minCleanupInterval is 1 hour
  }

  @Test
  void testCalculateNewDurationWhenLowCleanup() {
    // given
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", 20);

    numDeletedRecords.put("auditLog", 20);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

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
    verify(decisionInstanceWriter)
        .cleanupHistory(PARTITION_ID, CLEANUP_DATE, CHILD_ENTITY_BATCH_SIZE);
    verify(auditLogWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, CHILD_ENTITY_BATCH_SIZE);
  }

  @Test
  void testCalculateNewDurationWhenStandaloneDecisionsHitLimit() {
    // given - standalone decision instances hit the cleanup batch size limit
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", 0);
    numDeletedRecords.put("standaloneDecisionInstance", CHILD_ENTITY_BATCH_SIZE); // hit the limit
    numDeletedRecords.put("standaloneDecisionAuditLog", 500);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then - should halve the interval because standalone decisions hit limit
    assertThat(nextDuration).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void testCalculateNewDurationWhenStandaloneAuditLogsHitLimit() {
    // given - standalone audit logs hit the cleanup batch size limit
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", 0);
    numDeletedRecords.put("standaloneDecisionInstance", 500);
    numDeletedRecords.put("standaloneDecisionAuditLog", CHILD_ENTITY_BATCH_SIZE); // hit the limit

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then - should halve the interval because audit logs hit limit
    assertThat(nextDuration).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void testCalculateNewDurationWithStandaloneRecordsButNoLimit() {
    // given - some standalone records deleted but not hitting limit
    // processInstance: 10 (< 50 threshold for PI batch size PROCESS_INSTANCE_BATCH_SIZE)
    // max(standaloneDecision=20, auditLog=15) = 20 (< 50 threshold for cleanup batch size
    // PROCESS_INSTANCE_BATCH_SIZE)
    // Both are below their respective thresholds, so interval should be doubled
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", 10);
    numDeletedRecords.put("standaloneDecisionInstance", 20);
    numDeletedRecords.put("standaloneDecisionAuditLog", 15);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then - should double the interval (both PIs and other entities below half threshold)
    assertThat(nextDuration).isEqualTo(Duration.ofHours(8));
  }

  @Test
  void testCalculateNewDurationWithHighProcessInstancesOnly() {
    // given - process instances above threshold but other entities below
    // processInstance: 60 (>= 50 threshold but < PROCESS_INSTANCE_BATCH_SIZE limit)
    // max(standaloneDecision=10, auditLog=5) = 10 (< 50 threshold)
    // PIs are above threshold, so interval should remain unchanged
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", 60);
    numDeletedRecords.put("standaloneDecisionInstance", 10);
    numDeletedRecords.put("standaloneDecisionAuditLog", 5);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then - should keep interval unchanged (PIs above half threshold but not at limit)
    assertThat(nextDuration).isEqualTo(Duration.ofHours(4));
  }

  @Test
  void testCalculateNewDurationWithHighOtherEntitiesOnly() {
    // given - other entities above threshold but PIs below
    // processInstance: 10 (< 50 threshold)
    // max(standaloneDecision=600, auditLog=300) = 600 (>= 500 threshold but <
    // CHILD_ENTITY_BATCH_SIZE limit)
    // Other entities are above threshold, so interval should remain unchanged
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", 10);
    numDeletedRecords.put("standaloneDecisionInstance", 600);
    numDeletedRecords.put("standaloneDecisionAuditLog", 300);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords, null);

    // then - should keep interval unchanged (other entities above half threshold but not at limit)
    assertThat(nextDuration).isEqualTo(Duration.ofHours(4));
  }

  @Test
  void testCalculateNewDurationUsageLimitEnforcedWhenCleanupTookTooLong() {
    // given - cleanup hit max records (interval would be halved) but cleanup also took too long
    // lastDuration=40s, PIs hit limit -> halved to 20s (min interval overridden to 1s)
    // cleanupDuration=12s, maxUsage=25% -> required = 12s / 0.25 = 48s > 20s
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", PROCESS_INSTANCE_BATCH_SIZE); // hit limit

    // when
    when(historyConfig.minHistoryCleanupInterval()).thenReturn(Duration.ofSeconds(1));
    historyCleanupService = new HistoryCleanupService(config, rdbmsWriters, processInstanceReader);
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(
            Duration.ofSeconds(40), numDeletedRecords, Duration.ofSeconds(12));

    // then - usage limit (25%) requires interval >= 36s, larger than the halved interval 20s
    assertThat(nextDuration).isEqualTo(Duration.ofSeconds(36));
  }

  @Test
  void testCalculateNewDurationUsageLimitNotEnforcedWhenCleanupWasFast() {
    // given - cleanup hit max records (interval would be halved) but cleanup was fast
    // lastDuration=40s, PIs hit limit -> halved to 20s (min interval overridden to 1s)
    // cleanupDuration=1s, maxUsage=25% -> required = 1s / 0.25 = 4s < 20s (halved interval wins)
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", PROCESS_INSTANCE_BATCH_SIZE); // hit limit

    // when
    when(historyConfig.minHistoryCleanupInterval()).thenReturn(Duration.ofSeconds(1));
    historyCleanupService = new HistoryCleanupService(config, rdbmsWriters, processInstanceReader);
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(
            Duration.ofSeconds(40), numDeletedRecords, Duration.ofSeconds(1));

    // then - halved interval (20s) is larger than usage-limited interval (4s)
    assertThat(nextDuration).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void testCalculateNewDurationUsageLimitCappedAtMaxCleanupInterval() {
    // given - tiny maxUsage makes required interval exceed maxCleanupInterval
    // maxCleanupInterval = 1 day = 86400s
    // cleanupDuration=12s, maxUsage=0.0001 -> required = 12s / 0.0001 = 120000s > 86400s
    final var numDeletedRecords = new java.util.HashMap<String, Integer>();
    numDeletedRecords.put("rootProcessInstance", PROCESS_INSTANCE_BATCH_SIZE); // hit limit

    // when
    when(historyConfig.minHistoryCleanupInterval()).thenReturn(Duration.ofSeconds(1));
    when(historyConfig.maxHistoryCleanupUsage()).thenReturn(0.0001);
    historyCleanupService = new HistoryCleanupService(config, rdbmsWriters, processInstanceReader);
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(
            Duration.ofSeconds(40), numDeletedRecords, Duration.ofSeconds(12));

    // then - capped at maxCleanupInterval = 1 day
    assertThat(nextDuration).isEqualTo(Duration.ofDays(1));
  }

  static Stream<Arguments> ensureWithinMaxHistoryCleanupUsageTestCases() {
    return Stream.of(
        Arguments.of(
            "enforces limit when cleanup took too long",
            Duration.ofSeconds(20),
            Duration.ofSeconds(12),
            0.25,
            Duration.ofSeconds(36)), // 12s / 0.25 - 12s = 36s
        Arguments.of(
            "does not reduce interval when cleanup was fast",
            Duration.ofSeconds(20),
            Duration.ofSeconds(1),
            0.25,
            Duration.ofSeconds(20)), // 1s / 0.25 - 1s = 3s < 20s, proposed wins
        Arguments.of(
            "applies stricter usage limit",
            Duration.ofSeconds(20),
            Duration.ofSeconds(12),
            0.10,
            Duration.ofSeconds(108)), // 12s / 0.10 - 12s = 108s
        Arguments.of(
            "returns proposed when usage limit is disabled",
            Duration.ofSeconds(20),
            Duration.ofSeconds(12),
            0.0,
            Duration.ofSeconds(20)),
        Arguments.of(
            "returns proposed when cleanupDuration is null",
            Duration.ofSeconds(20),
            null,
            0.25,
            Duration.ofSeconds(20)),
        Arguments.of(
            "returns proposed when cleanupDuration is zero",
            Duration.ofSeconds(20),
            Duration.ZERO,
            0.25,
            Duration.ofSeconds(20)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ensureWithinMaxHistoryCleanupUsageTestCases")
  void testEnsureWithinMaxHistoryCleanupUsage(
      final String description,
      final Duration proposedDuration,
      final Duration cleanupDuration,
      final double maxHistoryCleanupUsage,
      final Duration expectedDuration) {
    when(historyConfig.maxHistoryCleanupUsage()).thenReturn(maxHistoryCleanupUsage);
    historyCleanupService = new HistoryCleanupService(config, rdbmsWriters, processInstanceReader);

    final Duration result =
        historyCleanupService.ensureWithinMaxHistoryCleanupUsage(proposedDuration, cleanupDuration);

    assertThat(result).isEqualTo(expectedDuration);
  }
}
