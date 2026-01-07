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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.sql.ProcessInstanceDependantMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

public class HistoryDeletionServiceTest {

  private HistoryDeletionService historyDeletionService;
  private RdbmsWriters rdbmsWritersMock;
  private HistoryDeletionDbReader historyDeletionDbReaderMock;

  @BeforeEach
  void setUp() {
    rdbmsWritersMock = mock(RdbmsWriters.class, Answers.RETURNS_DEEP_STUBS);
    historyDeletionDbReaderMock = mock(HistoryDeletionDbReader.class);
    historyDeletionService =
        new HistoryDeletionService(
            rdbmsWritersMock,
            historyDeletionDbReaderMock,
            new HistoryDeletionConfig(Duration.ofSeconds(1), Duration.ofMinutes(5), 100, 10000));
  }

  @Test
  void shouldDeleteHistory() {
    // given
    final var partitionId = 1;
    final var processInstanceKey1 = 1L;
    final var processInstanceKey2 = 2L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            List.of(
                createModel(processInstanceKey1, partitionId),
                createModel(processInstanceKey2, partitionId)));
    final var mapperMock = mock(ProcessInstanceDependantMapper.class);
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(mapperMock)
        .deleteProcessInstanceRelatedData(
            argThat(
                dto ->
                    dto.processInstanceKeys()
                        .equals(List.of(processInstanceKey1, processInstanceKey2))));
    verify(rdbmsWritersMock.getProcessInstanceWriter())
        .deleteByKeys(List.of(processInstanceKey1, processInstanceKey2));
    verify(rdbmsWritersMock.getHistoryDeletionWriter())
        .deleteByResourceKeys(List.of(processInstanceKey1, processInstanceKey2));
  }

  @Test
  void shouldNotDeleteWhenBatchIsEmpty() {
    // given
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt())).thenReturn(List.of());
    final var mapperMock = mock(ProcessInstanceDependantMapper.class);
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));

    // when
    historyDeletionService.deleteHistory(1);

    // then
    verify(mapperMock, never()).deleteProcessInstanceRelatedData(any());
    verify(rdbmsWritersMock.getProcessInstanceWriter(), never()).deleteByKeys(anyList());
    verify(rdbmsWritersMock.getHistoryDeletionWriter(), never()).deleteByResourceKeys(anyList());
  }

  @Test
  void shouldNotDeleteProcessInstanceIfNotAllDependantDataDeleted() {
    // given
    final var partitionId = 1;
    final var processInstanceKey = 1L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(List.of(createModel(processInstanceKey, partitionId)));
    final var mapperMock = mock(ProcessInstanceDependantMapper.class);
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));
    when(mapperMock.deleteProcessInstanceRelatedData(any()))
        .thenReturn(10000); // not all dependant data deleted

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessInstanceWriter(), never()).deleteByKeys(anyList());
    verify(rdbmsWritersMock.getHistoryDeletionWriter(), never()).deleteByResourceKeys(anyList());
  }

  @Test
  void shouldApplyExponentialBackoffIfNothingToDelete() {
    // given
    final var partitionId = 1;

    // when
    final var interval1 = historyDeletionService.deleteHistory(partitionId);
    final var interval2 = historyDeletionService.deleteHistory(partitionId);
    final var interval3 = historyDeletionService.deleteHistory(partitionId);
    final var interval4 = historyDeletionService.deleteHistory(partitionId);
    final var interval5 = historyDeletionService.deleteHistory(partitionId);

    // then
    assertThat(interval1).isEqualTo(Duration.ofSeconds(2));
    assertThat(interval2).isEqualTo(Duration.ofSeconds(4));
    assertThat(interval3).isEqualTo(Duration.ofSeconds(8));
    assertThat(interval4).isEqualTo(Duration.ofSeconds(16));
    assertThat(interval5).isEqualTo(Duration.ofSeconds(32));
  }

  @Test
  void shouldImmediatelyContinueIfDeletionsWereMade() {
    // given
    final var partitionId = 1;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(List.of(createModel(1L, partitionId)));
    when(rdbmsWritersMock.getProcessInstanceDependantWriters()).thenReturn(Collections.emptyList());
    when(rdbmsWritersMock.getHistoryDeletionWriter().deleteByResourceKeys(anyList())).thenReturn(1);

    // when
    final var interval1 = historyDeletionService.deleteHistory(partitionId);
    final var interval2 = historyDeletionService.deleteHistory(partitionId);

    // then
    assertThat(interval1).isEqualTo(Duration.ofSeconds(1));
    assertThat(interval2).isEqualTo(Duration.ofSeconds(1));
  }

  private static HistoryDeletionDbModel createModel(
      final long processInstanceKey, final int partitionId) {
    return new HistoryDeletionDbModel(
        processInstanceKey, HistoryDeletionTypeDbModel.PROCESS_INSTANCE, 2L, partitionId);
  }

  private static class TestProcessInstanceDependantWriter extends ProcessInstanceDependant {
    public TestProcessInstanceDependantWriter(
        final ProcessInstanceDependantMapper processInstanceDependantMapper) {
      super(processInstanceDependantMapper);
    }
  }
}
