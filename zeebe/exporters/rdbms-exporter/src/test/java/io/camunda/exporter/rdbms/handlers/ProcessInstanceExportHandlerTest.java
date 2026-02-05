/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.exporter.rdbms.utils.TreePath;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProcessInstanceExportHandlerTest {
  private static final long ROOT_PROCESS_INSTANCE_KEY = 1L;
  private static final long SUB_PROCESS_INSTANCE_KEY = 2L;

  private final ProcessInstanceWriter processInstanceWriter = mock(ProcessInstanceWriter.class);
  private final HistoryCleanupService historyCleanupService = mock(HistoryCleanupService.class);
  private ProcessInstanceExportHandler handler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    final ExporterEntityCache<Long, CachedProcessEntity> processCache =
        mock(ExporterEntityCache.class);
    handler =
        new ProcessInstanceExportHandler(
            processInstanceWriter, historyCleanupService, processCache, 10);
  }

  @Test
  void shouldCreateTreePathForSimpleProcess() {
    // given
    final var record = TestRecordFactory.simpleProcess();
    try (final var mocked = Mockito.mockStatic(ProcessCacheUtil.class)) {
      mocked
          .when(() -> ProcessCacheUtil.getCallActivityIds(any(), any()))
          .thenReturn(List.of(List.of(100L)));
      // when
      final TreePath path = handler.createTreePath(record);
      // then
      assertThat(path.toTruncatedString()).isEqualTo("PI_1");
    }
  }

  @Test
  void shouldCreateTreePathForRecursiveProcess() {
    // given
    final var record = TestRecordFactory.recursiveProcess();
    try (final var mocked = Mockito.mockStatic(ProcessCacheUtil.class)) {
      mocked
          .when(() -> ProcessCacheUtil.getCallActivityIds(any(), any()))
          .thenReturn(List.of(List.of(200L), List.of(201L)));
      // when
      final TreePath path = handler.createTreePath(record);
      // then
      // The expected string depends on the TreePath implementation, but should contain both process
      // instance keys and flow node IDs
      assertThat(path.toTruncatedString()).contains("PI_2");
      assertThat(path.toTruncatedString()).contains("FN_20");
      // Do not check for raw '200' or '201' as the output uses 'FN_' prefix
    }
  }

  @Test
  void shouldFallbackToIndexOnCacheMiss() {
    // given
    final var record = TestRecordFactory.simpleProcess();
    try (final var mocked = Mockito.mockStatic(ProcessCacheUtil.class)) {
      mocked
          .when(() -> ProcessCacheUtil.getCallActivityIds(any(), any()))
          .thenReturn(List.of()); // empty cache
      // when
      final TreePath path = handler.createTreePath(record);
      // then
      assertThat(path.toTruncatedString()).isEqualTo("PI_1");
    }
  }

  @Test
  void shouldReturnNonEmptyTreePathForEmptyPath() {
    // given
    final var record = TestRecordFactory.emptyPath();
    // when
    final TreePath path = handler.createTreePath(record);
    // then
    assertThat(path).isNotNull();
    assertThat(path.toTruncatedString()).isNotEmpty();
  }

  @Test
  void shouldScheduleHistoryCleanupForCompletedRootProcessInstance() {
    // given
    final var record = TestRecordFactory.rootProcess(ProcessInstanceIntent.ELEMENT_COMPLETED);
    // when
    handler.export(record);
    // then
    verify(processInstanceWriter)
        .finish(eq(ROOT_PROCESS_INSTANCE_KEY), eq(ProcessInstanceState.COMPLETED), any());
    verify(historyCleanupService)
        .scheduleProcessForHistoryCleanup(eq(ROOT_PROCESS_INSTANCE_KEY), any());
  }

  @Test
  void shouldScheduleHistoryCleanupForTerminatedRootProcessInstance() {
    // given
    final var record = TestRecordFactory.rootProcess(ProcessInstanceIntent.ELEMENT_TERMINATED);
    // when
    handler.export(record);
    // then
    verify(processInstanceWriter)
        .finish(eq(ROOT_PROCESS_INSTANCE_KEY), eq(ProcessInstanceState.CANCELED), any());
    verify(historyCleanupService)
        .scheduleProcessForHistoryCleanup(eq(ROOT_PROCESS_INSTANCE_KEY), any());
  }

  @Test
  void shouldNotScheduleHistoryCleanupForCompletedNonRootProcessInstance() {
    // given
    final var record = TestRecordFactory.subProcess(ProcessInstanceIntent.ELEMENT_COMPLETED);
    // when
    handler.export(record);
    // then
    verify(processInstanceWriter)
        .finish(eq(SUB_PROCESS_INSTANCE_KEY), eq(ProcessInstanceState.COMPLETED), any());
    verifyNoInteractions(historyCleanupService);
  }

  @Test
  void shouldNotScheduleHistoryCleanupForTerminatedNonRootProcessInstance() {
    // given
    final var record = TestRecordFactory.subProcess(ProcessInstanceIntent.ELEMENT_TERMINATED);
    // when
    handler.export(record);
    // then
    verify(processInstanceWriter)
        .finish(eq(SUB_PROCESS_INSTANCE_KEY), eq(ProcessInstanceState.CANCELED), any());
    verifyNoInteractions(historyCleanupService);
  }

  // Helper factory for test records
  static class TestRecordFactory {
    static Record<ProcessInstanceRecordValue> simpleProcess() {
      return rootProcess(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    }

    static Record<ProcessInstanceRecordValue> rootProcess(final ProcessInstanceIntent intent) {
      final var value = mock(ProcessInstanceRecordValue.class);
      when(value.getElementInstancePath()).thenReturn(List.of(List.of(1L)));
      when(value.getProcessDefinitionPath()).thenReturn(List.of(10L));
      when(value.getCallingElementPath()).thenReturn(List.of(0));
      when(value.getProcessInstanceKey()).thenReturn(ROOT_PROCESS_INSTANCE_KEY);
      when(value.getRootProcessInstanceKey()).thenReturn(ROOT_PROCESS_INSTANCE_KEY);
      return mockRecord(value, intent);
    }

    static Record<ProcessInstanceRecordValue> subProcess(final ProcessInstanceIntent intent) {
      final var value = mock(ProcessInstanceRecordValue.class);
      when(value.getElementInstancePath()).thenReturn(List.of(List.of(1L)));
      when(value.getProcessDefinitionPath()).thenReturn(List.of(10L));
      when(value.getCallingElementPath()).thenReturn(List.of(0));
      when(value.getProcessInstanceKey()).thenReturn(SUB_PROCESS_INSTANCE_KEY);
      when(value.getRootProcessInstanceKey()).thenReturn(ROOT_PROCESS_INSTANCE_KEY);
      return mockRecord(value, intent);
    }

    static Record<ProcessInstanceRecordValue> recursiveProcess() {
      final var value = mock(ProcessInstanceRecordValue.class);
      when(value.getElementInstancePath()).thenReturn(List.of(List.of(2L), List.of(3L)));
      when(value.getProcessDefinitionPath()).thenReturn(List.of(20L, 21L));
      when(value.getCallingElementPath()).thenReturn(List.of(0, 1));
      when(value.getProcessInstanceKey()).thenReturn(3L);
      return mockRecord(value);
    }

    static Record<ProcessInstanceRecordValue> emptyPath() {
      final var value = mock(ProcessInstanceRecordValue.class);
      when(value.getElementInstancePath()).thenReturn(List.of());
      when(value.getProcessInstanceKey()).thenReturn(99L);
      return mockRecord(value);
    }

    @SuppressWarnings("unchecked")
    private static Record<ProcessInstanceRecordValue> mockRecord(
        final ProcessInstanceRecordValue value) {
      return mockRecord(value, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    }

    @SuppressWarnings("unchecked")
    private static Record<ProcessInstanceRecordValue> mockRecord(
        final ProcessInstanceRecordValue value, final ProcessInstanceIntent intent) {
      final var record = mock(Record.class);
      when(record.getValue()).thenReturn(value);
      when(record.getKey()).thenReturn(42L);
      when(record.getIntent()).thenReturn(intent);
      return record;
    }
  }
}
