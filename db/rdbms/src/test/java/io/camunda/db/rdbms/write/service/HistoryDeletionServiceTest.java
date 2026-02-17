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
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.sql.ProcessInstanceDependantMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.HistoryDeletionBatch;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.SearchQueryResult;
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
  private ProcessInstanceDbReader processInstanceDbReaderMock;

  @BeforeEach
  void setUp() {
    rdbmsWritersMock = mock(RdbmsWriters.class, Answers.RETURNS_DEEP_STUBS);
    historyDeletionDbReaderMock = mock(HistoryDeletionDbReader.class);
    processInstanceDbReaderMock = mock(ProcessInstanceDbReader.class);
    historyDeletionService =
        new HistoryDeletionService(
            rdbmsWritersMock,
            historyDeletionDbReaderMock,
            processInstanceDbReaderMock,
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
            new HistoryDeletionBatch(
                List.of(
                    createModel(processInstanceKey1, HistoryDeletionTypeDbModel.PROCESS_INSTANCE),
                    createModel(
                        processInstanceKey2, HistoryDeletionTypeDbModel.PROCESS_INSTANCE))));
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
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(new HistoryDeletionBatch(List.of()));
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
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(processInstanceKey, HistoryDeletionTypeDbModel.PROCESS_INSTANCE))));
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
  void shouldDeleteProcessDefinitionHistory() {
    // given
    final var partitionId = 1;
    final var processDefinitionKey1 = 1L;
    final var processDefinitionKey2 = 2L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(
                        processDefinitionKey1, HistoryDeletionTypeDbModel.PROCESS_DEFINITION),
                    createModel(
                        processDefinitionKey2, HistoryDeletionTypeDbModel.PROCESS_DEFINITION))));
    when(processInstanceDbReaderMock.search(any())).thenReturn(SearchQueryResult.empty());

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessDefinitionWriter())
        .deleteByKeys(List.of(processDefinitionKey1, processDefinitionKey2));
    verify(rdbmsWritersMock.getHistoryDeletionWriter())
        .deleteByResourceKeys(List.of(processDefinitionKey1, processDefinitionKey2));
  }

  @Test
  void shouldNotDeleteProcessDefinitionIfNotAllDependantProcessInstancesDeleted() {
    // given
    final var partitionId = 1;
    final var processDefinitionKey = 1L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(
                        processDefinitionKey, HistoryDeletionTypeDbModel.PROCESS_DEFINITION))));
    when(processInstanceDbReaderMock.search(any()))
        .thenReturn(SearchQueryResult.of(mock(ProcessInstanceEntity.class)));

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessDefinitionWriter(), never()).deleteByKeys(anyList());
    verify(rdbmsWritersMock.getHistoryDeletionWriter(), never()).deleteByResourceKeys(anyList());
  }

  @Test
  void shouldApplyExponentialBackoffIfNothingToDelete() {
    // given
    final var partitionId = 1;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(new HistoryDeletionBatch(List.of()));

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
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(createModel(1L, HistoryDeletionTypeDbModel.PROCESS_INSTANCE))));
    when(rdbmsWritersMock.getProcessInstanceDependantWriters()).thenReturn(Collections.emptyList());
    when(rdbmsWritersMock.getHistoryDeletionWriter().deleteByResourceKeys(anyList())).thenReturn(1);

    // when
    final var interval1 = historyDeletionService.deleteHistory(partitionId);
    final var interval2 = historyDeletionService.deleteHistory(partitionId);

    // then
    assertThat(interval1).isEqualTo(Duration.ofSeconds(1));
    assertThat(interval2).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void shouldDeletePIsFromHistoryDeletionTableIfProcessDefinitionDeletionFailed() {
    // given
    final var partitionId = 1;
    final var processInstanceKey1 = 1L;
    final var processDefinitionKey1 = 2L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(processInstanceKey1, HistoryDeletionTypeDbModel.PROCESS_INSTANCE),
                    createModel(
                        processDefinitionKey1, HistoryDeletionTypeDbModel.PROCESS_DEFINITION))));
    when(rdbmsWritersMock.getProcessInstanceDependantWriters()).thenReturn(Collections.emptyList());
    when(processInstanceDbReaderMock.search(any()))
        .thenReturn(SearchQueryResult.of(mock(ProcessInstanceEntity.class)));

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessInstanceWriter()).deleteByKeys(List.of(processInstanceKey1));
    verify(rdbmsWritersMock.getProcessDefinitionWriter(), never())
        .deleteByKeys(List.of(processDefinitionKey1));
    verify(rdbmsWritersMock.getHistoryDeletionWriter())
        .deleteByResourceKeys(List.of(processInstanceKey1));
  }

  @Test
  void shouldDeleteDecisionInstanceHistory() {
    // given
    final var partitionId = 1;
    final var decisionInstanceKey1 = 1L;
    final var decisionInstanceKey2 = 2L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(decisionInstanceKey1, HistoryDeletionTypeDbModel.DECISION_INSTANCE),
                    createModel(
                        decisionInstanceKey2, HistoryDeletionTypeDbModel.DECISION_INSTANCE))));

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getDecisionInstanceWriter())
        .deleteByKeys(List.of(decisionInstanceKey1, decisionInstanceKey2));
    verify(rdbmsWritersMock.getHistoryDeletionWriter())
        .deleteByResourceKeys(List.of(decisionInstanceKey1, decisionInstanceKey2));
  }

  @Test
  void shouldDeleteDecisionInstancesIfProcessInstanceDeletionFailed() {
    // given
    final var partitionId = 1;
    final var processInstanceKey1 = 1L;
    final var decisionInstanceKey1 = 2L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(processInstanceKey1, HistoryDeletionTypeDbModel.PROCESS_INSTANCE),
                    createModel(
                        decisionInstanceKey1, HistoryDeletionTypeDbModel.DECISION_INSTANCE))));
    // Mock process instance dependant writers to return limit, meaning not all data deleted
    final var mapperMock = mock(ProcessInstanceDependantMapper.class);
    when(mapperMock.deleteProcessInstanceRelatedData(any()))
        .thenReturn(10000); // return the limit, meaning not all dependant data deleted
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessInstanceWriter(), never())
        .deleteByKeys(List.of(processInstanceKey1));
    verify(rdbmsWritersMock.getDecisionInstanceWriter())
        .deleteByKeys(List.of(decisionInstanceKey1));
    verify(rdbmsWritersMock.getHistoryDeletionWriter())
        .deleteByResourceKeys(List.of(decisionInstanceKey1));
  }

  @Test
  void shouldDeletePIsAndDIsFromDeletionIndexIfProcessDefinitionDeletionFailed() {
    // given
    final var partitionId = 1;
    final var processInstanceKey1 = 1L;
    final var decisionInstanceKey1 = 2L;
    final var processDefinitionKey1 = 3L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(processInstanceKey1, HistoryDeletionTypeDbModel.PROCESS_INSTANCE),
                    createModel(decisionInstanceKey1, HistoryDeletionTypeDbModel.DECISION_INSTANCE),
                    createModel(
                        processDefinitionKey1, HistoryDeletionTypeDbModel.PROCESS_DEFINITION))));
    when(rdbmsWritersMock.getProcessInstanceDependantWriters()).thenReturn(Collections.emptyList());
    when(processInstanceDbReaderMock.search(any()))
        .thenReturn(SearchQueryResult.of(mock(ProcessInstanceEntity.class)));

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessInstanceWriter()).deleteByKeys(List.of(processInstanceKey1));
    verify(rdbmsWritersMock.getProcessDefinitionWriter(), never())
        .deleteByKeys(List.of(processDefinitionKey1));
    verify(rdbmsWritersMock.getDecisionInstanceWriter())
        .deleteByKeys(List.of(decisionInstanceKey1));
    verify(rdbmsWritersMock.getHistoryDeletionWriter())
        .deleteByResourceKeys(List.of(processInstanceKey1, decisionInstanceKey1));
  }

  private static HistoryDeletionDbModel createModel(
      final long processInstanceKey, final HistoryDeletionTypeDbModel type) {
    return new HistoryDeletionDbModel(processInstanceKey, type, 2L, 1);
  }

  private static class TestProcessInstanceDependantWriter extends ProcessInstanceDependant {
    public TestProcessInstanceDependantWriter(
        final ProcessInstanceDependantMapper processInstanceDependantMapper) {
      super(processInstanceDependantMapper);
    }
  }
}
