/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.sql.HistoryDeletionMapper;
import org.junit.jupiter.api.Test;

class HistoryDeletionDbReaderTest {
  private final HistoryDeletionMapper historyDeletionMapper = mock(HistoryDeletionMapper.class);
  private final HistoryDeletionDbReader historyDeletionDbReader =
      new HistoryDeletionDbReader(historyDeletionMapper);

  @Test
  void shouldReturnEmptyList() {
    final var nextBatch = historyDeletionDbReader.getNextBatch(0, 1);
    assertThat(nextBatch.historyDeletionModels()).isEmpty();
  }
}
