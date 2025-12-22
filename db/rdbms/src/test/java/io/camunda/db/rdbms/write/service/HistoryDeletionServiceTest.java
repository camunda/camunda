/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
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
        new HistoryDeletionService(rdbmsWritersMock, historyDeletionDbReaderMock);
  }

  @Test
  void shouldGetNextBatchOfResourcesToDelete() {
    // given
    final var partitionId = 1;

    // when
    historyDeletionService.deleteHistory(partitionId);

    // then
    verify(historyDeletionDbReaderMock).getNextBatch(partitionId, 100);
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
        .thenReturn(
            List.of(
                new HistoryDeletionDbModel(
                    1L, HistoryDeletionTypeDbModel.PROCESS_INSTANCE, 2L, partitionId)));
    when(rdbmsWritersMock.getProcessInstanceDependantWriters()).thenReturn(Collections.emptyList());
    when(rdbmsWritersMock.getHistoryDeletionWriter().deleteByResourceKeys(anyList())).thenReturn(1);

    // when
    final var interval1 = historyDeletionService.deleteHistory(partitionId);
    final var interval2 = historyDeletionService.deleteHistory(partitionId);

    // then
    assertThat(interval1).isEqualTo(Duration.ofSeconds(1));
    assertThat(interval2).isEqualTo(Duration.ofSeconds(1));
  }
}
