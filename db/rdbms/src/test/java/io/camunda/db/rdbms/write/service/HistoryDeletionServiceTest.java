/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HistoryDeletionServiceTest {

  private HistoryDeletionService historyDeletionService;
  private RdbmsWriters rdbmsWritersMock;
  private HistoryDeletionDbReader historyDeletionDbReaderMock;

  @BeforeEach
  void setUp() {
    rdbmsWritersMock = mock(RdbmsWriters.class);
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
}
