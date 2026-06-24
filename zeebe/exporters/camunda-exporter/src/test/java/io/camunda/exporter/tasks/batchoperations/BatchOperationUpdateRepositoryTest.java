/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BatchOperationUpdateRepositoryTest {

  @Test
  void shouldReturnCorrectOperationCounts() {
    // given
    final OperationsAggData data =
        new OperationsAggData(
            "1",
            Map.of("SCHEDULED", 3L, "COMPLETED", 3L, "SKIPPED", 1L, "FAILED", 2L, "LOCKED", 1L));

    // then
    assertThat(data.getFinishedOperationsCount()).isEqualTo(6L);
    assertThat(data.getCompletedOperationsCount()).isEqualTo(4L);
    assertThat(data.getFailedOperationsCount()).isEqualTo(2L);
    assertThat(data.getTotalOperationsCount()).isEqualTo(10L);
  }
}
