/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceBatchSizes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchiveBatchTest {
  @Test
  void testSplitWhenAllDocCountsZero() {
    final var sizes = new ProcessInstanceBatchSizes(Map.of(), Map.of());

    final ProcessInstanceArchiveBatch batch =
        new ProcessInstanceArchiveBatch("date", List.of(1L, 2L), List.of(10L));

    assertThat(sizes.splitBatch(batch, 30L))
        .isEqualTo(List.of(new ProcessInstanceArchiveBatch("date", List.of(1L, 2L), List.of(10L))));
  }

  @Test
  void test() {
    final var sizes =
        new ProcessInstanceBatchSizes(
            Map.ofEntries(
                Map.entry(1L, 10L), Map.entry(2L, 10L), Map.entry(3L, 10L), Map.entry(4L, 10L)),
            Map.ofEntries(
                Map.entry(10L, 10L),
                Map.entry(20L, 100L),
                Map.entry(30L, 10L),
                Map.entry(40L, 10L)));

    final ProcessInstanceArchiveBatch batch =
        new ProcessInstanceArchiveBatch(
            "date", List.of(1L, 2L, 3L, 4L, 5L, 6L), List.of(10L, 20L, 30L, 40L));

    assertThat(sizes.splitBatch(batch, 30L))
        .isEqualTo(
            List.of(
                new ProcessInstanceArchiveBatch("date", List.of(1L, 2L, 3L), List.of()),
                new ProcessInstanceArchiveBatch("date", List.of(4L, 5L, 6L), List.of(10L)),
                new ProcessInstanceArchiveBatch("date", List.of(), List.of(20L)),
                new ProcessInstanceArchiveBatch("date", List.of(), List.of(30L, 40L))));
  }
}
