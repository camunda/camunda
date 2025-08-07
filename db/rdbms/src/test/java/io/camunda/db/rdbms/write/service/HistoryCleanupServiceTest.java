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

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.search.entities.BatchOperationType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HistoryCleanupServiceTest {

  private static final int PARTITION_ID = 1;
  private static final OffsetDateTime CLEANUP_DATE = OffsetDateTime.now();

  private RdbmsWriterConfig config;
  private ProcessInstanceWriter processInstanceWriter;
  private IncidentWriter incidentWriter;
  private FlowNodeInstanceWriter flowNodeInstanceWriter;
  private UserTaskWriter userTaskWriter;
  private VariableWriter variableInstanceWriter;
  private DecisionInstanceWriter decisionInstanceWriter;
  private JobWriter jobWriter;
  private SequenceFlowWriter sequenceFlowWriter;
  private BatchOperationWriter batchOperationWriter;

  private HistoryCleanupService historyCleanupService;

  @BeforeEach
  void setUp() {
    config = mock(RdbmsWriterConfig.class);
    processInstanceWriter = mock(ProcessInstanceWriter.class);
    incidentWriter = mock(IncidentWriter.class);
    flowNodeInstanceWriter = mock(FlowNodeInstanceWriter.class);
    userTaskWriter = mock(UserTaskWriter.class);
    variableInstanceWriter = mock(VariableWriter.class);
    decisionInstanceWriter = mock(DecisionInstanceWriter.class);
    jobWriter = mock(JobWriter.class);
    sequenceFlowWriter = mock(SequenceFlowWriter.class);
    batchOperationWriter = mock(BatchOperationWriter.class);

    when(processInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(flowNodeInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(incidentWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(userTaskWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(variableInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(decisionInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(jobWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(sequenceFlowWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(0);
    when(batchOperationWriter.cleanupItemHistory(any(), anyInt())).thenReturn(0);
    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(0);

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

    historyCleanupService =
        new HistoryCleanupService(
            config,
            processInstanceWriter,
            incidentWriter,
            flowNodeInstanceWriter,
            userTaskWriter,
            variableInstanceWriter,
            decisionInstanceWriter,
            jobWriter,
            sequenceFlowWriter,
            batchOperationWriter,
            mock(RdbmsWriterMetrics.class, Mockito.RETURNS_DEEP_STUBS));
  }

  @Test
  void testFirstCleanupHistory() {
    // given
    when(processInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(flowNodeInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(incidentWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(userTaskWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(variableInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(decisionInstanceWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(jobWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(sequenceFlowWriter.cleanupHistory(anyInt(), any(), anyInt())).thenReturn(1);
    when(batchOperationWriter.cleanupItemHistory(any(), anyInt())).thenReturn(1);
    when(batchOperationWriter.cleanupHistory(any(), anyInt())).thenReturn(1);

    // when
    final Duration nextCleanupInterval =
        historyCleanupService.cleanupHistory(PARTITION_ID, CLEANUP_DATE);

    // then
    assertThat(nextCleanupInterval).isEqualTo(Duration.ofHours(1));
    verify(processInstanceWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(flowNodeInstanceWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(incidentWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(userTaskWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(variableInstanceWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(decisionInstanceWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(jobWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(sequenceFlowWriter).cleanupHistory(PARTITION_ID, CLEANUP_DATE, 100);
    verify(batchOperationWriter).cleanupItemHistory(CLEANUP_DATE, 100);
    verify(batchOperationWriter).cleanupHistory(CLEANUP_DATE, 100);
  }

  @Test
  void testCalculateNewDurationWhenDeletedNothing() {
    // given
    final Map<String, Integer> numDeletedRecords =
        Map.of(
            "processInstance", 0,
            "flowNodeInstance", 0,
            "incident", 0,
            "userTask", 0,
            "variable", 0,
            "decisionInstance", 0,
            "job", 0,
            "sequenceFlow", 0,
            "batchOperation", 0);

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
    final Map<String, Integer> numDeletedRecords =
        Map.of(
            "processInstance", 100,
            "flowNodeInstance", 100,
            "incident", 100,
            "userTask", 100,
            "variable", 100,
            "decisionInstance", 100,
            "job", 100,
            "sequenceFlow", 100,
            "batchOperationItem", 100,
            "batchOperation", 100);

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
    final Map<String, Integer> numDeletedRecords =
        Map.of(
            "processInstance", 50,
            "flowNodeInstance", 50,
            "incident", 50,
            "userTask", 50,
            "variable", 50,
            "decisionInstance", 50,
            "job", 50,
            "sequenceFlow", 50,
            "batchOperation", 50);

    // when
    final Duration nextDuration =
        historyCleanupService.calculateNewDuration(Duration.ofHours(4), numDeletedRecords);

    // then
    assertThat(nextDuration)
        .isEqualTo(Duration.ofHours(4)); // assuming minCleanupInterval is 1 hour
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
}
