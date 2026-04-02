/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArchiveBatchTest {

  @Test
  void shouldChunkEmptyProcessInstanceBatch() {
    final var batch =
        new ArchiveBatch.ProcessInstanceArchiveBatch("finished-date", List.of(), List.of());

    assertThat(batch.chunk(10))
        .isEqualTo(
            List.of(
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(), List.of())));
  }

  @ParameterizedTest
  @MethodSource("shouldChunkProcessInstanceBatchArguments")
  void shouldChunkProcessInstanceBatch(
      final int chunkSize, final List<ArchiveBatch.ProcessInstanceArchiveBatch> expected) {
    final var batch =
        new ArchiveBatch.ProcessInstanceArchiveBatch(
            "finished-date", List.of(1L, 2L, 3L), List.of(1L, 2L, 3L));

    assertThat(batch.chunk(chunkSize)).isEqualTo(expected);
  }

  private static Stream<Arguments> shouldChunkProcessInstanceBatchArguments() {
    return Stream.of(
        Arguments.of(
            10,
            List.of(
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(1L, 2L, 3L), List.of(1L, 2L, 3L)))),
        Arguments.of(
            6,
            List.of(
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(1L, 2L, 3L), List.of(1L, 2L, 3L)))),
        Arguments.of(
            5,
            List.of(
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(1L, 2L), List.of(1L, 2L, 3L)),
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(3L), List.of()))),
        Arguments.of(
            3,
            List.of(
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(), List.of(1L, 2L, 3L)),
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(1L, 2L, 3L), List.of()))),
        Arguments.of(
            1,
            List.of(
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(), List.of(1L)),
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(), List.of(2L)),
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(), List.of(3L)),
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(1L), List.of()),
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(2L), List.of()),
                new ArchiveBatch.ProcessInstanceArchiveBatch(
                    "finished-date", List.of(3L), List.of()))));
  }
}
