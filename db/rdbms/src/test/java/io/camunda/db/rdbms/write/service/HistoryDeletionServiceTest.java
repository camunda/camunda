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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.sql.RootProcessInstanceDependantMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.HistoryDeletionBatch;
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
            mock(ProcessInstanceDbReader.class),
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
    final var mapperMock = mock(RootProcessInstanceDependantMapper.class);
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(mapperMock)
        .deleteRootProcessInstanceRelatedData(
            argThat(
                dto ->
                    dto.rootProcessInstanceKeys()
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
    final var mapperMock = mock(RootProcessInstanceDependantMapper.class);
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));

    // when
    historyDeletionService.deleteHistory(1);

    // then
    verify(mapperMock, never()).deleteRootProcessInstanceRelatedData(any());
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
    final var mapperMock = mock(RootProcessInstanceDependantMapper.class);
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));
    when(mapperMock.deleteRootProcessInstanceRelatedData(any()))
        .thenThrow(new RuntimeException("Failed deleting")); // not all dependant data deleted

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
    final var processInstanceKey = 1L;
    final HistoryDeletionBatch historyDeletionBatch =
        new HistoryDeletionBatch(
            List.of(
                createModel(processInstanceKey, HistoryDeletionTypeDbModel.PROCESS_INSTANCE),
                createModel(processDefinitionKey, HistoryDeletionTypeDbModel.PROCESS_DEFINITION)));
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(historyDeletionBatch);
    final var mapperMock = mock(RootProcessInstanceDependantMapper.class);
    when(rdbmsWritersMock.getProcessInstanceDependantWriters())
        .thenReturn(List.of(new TestProcessInstanceDependantWriter(mapperMock)));
    when(rdbmsWritersMock.getProcessInstanceWriter().deleteByKeys(List.of(processInstanceKey)))
        .thenThrow(new RuntimeException("Failed deleting")); // not all process instances deleted

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
        .thenReturn(
            new HistoryDeletionBatch(Collections.emptyList())); // nothing to delete in all calls

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
    final var processDefinitionWriterMock = mock(ProcessDefinitionWriter.class);
    doThrow(new RuntimeException("Failed deleting"))
        .when(processDefinitionWriterMock)
        .deleteByKeys(anyList()); // process definition deletion fails
    doReturn(processDefinitionWriterMock).when(rdbmsWritersMock).getProcessDefinitionWriter();

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessInstanceWriter()).deleteByKeys(List.of(processInstanceKey1));
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
  void shouldNotDeleteFromDeletionTableIfDecisionInstanceDeletionFailed() {
    // given
    final var partitionId = 1;
    final var decisionInstanceKey1 = 3L;
    when(historyDeletionDbReaderMock.getNextBatch(anyInt(), anyInt()))
        .thenReturn(
            new HistoryDeletionBatch(
                List.of(
                    createModel(
                        decisionInstanceKey1, HistoryDeletionTypeDbModel.DECISION_INSTANCE))));
    when(rdbmsWritersMock.getProcessInstanceDependantWriters()).thenReturn(Collections.emptyList());
    when(rdbmsWritersMock.getDecisionInstanceWriter().deleteByKeys(anyList()))
        .thenThrow(new RuntimeException("Failed deleting")); // decision instance deletion fails

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getHistoryDeletionWriter(), never()).deleteByResourceKeys(anyList());
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
    when(rdbmsWritersMock.getProcessInstanceDependantWriters()).thenReturn(Collections.emptyList());
    when(rdbmsWritersMock.getProcessInstanceWriter().deleteByKeys(anyList()))
        .thenThrow(new RuntimeException("Failed deleting")); // process instance deletion fails

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
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
    final var processDefinitionWriterMock = mock(ProcessDefinitionWriter.class);
    doThrow(new RuntimeException("Failed deleting"))
        .when(processDefinitionWriterMock)
        .deleteByKeys(anyList()); // process definition deletion fails
    doReturn(processDefinitionWriterMock).when(rdbmsWritersMock).getProcessDefinitionWriter();

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(rdbmsWritersMock.getProcessInstanceWriter()).deleteByKeys(List.of(processInstanceKey1));
    verify(rdbmsWritersMock.getDecisionInstanceWriter())
        .deleteByKeys(List.of(decisionInstanceKey1));
    verify(rdbmsWritersMock.getHistoryDeletionWriter())
        .deleteByResourceKeys(List.of(processInstanceKey1, decisionInstanceKey1));
  }

  private static HistoryDeletionDbModel createModel(
      final long resourceKey, final HistoryDeletionTypeDbModel type) {
    return new HistoryDeletionDbModel(resourceKey, type, 2L, 1);
  }

  private static class TestProcessInstanceDependantWriter extends RootProcessInstanceDependant {
    public TestProcessInstanceDependantWriter(
        final RootProcessInstanceDependantMapper rootProcessInstanceDependantMapper) {
      super(rootProcessInstanceDependantMapper);
    }
  }
}
