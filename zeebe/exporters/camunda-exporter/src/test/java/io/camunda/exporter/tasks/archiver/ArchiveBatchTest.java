/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArchiveBatchTest {

  @ParameterizedTest
  @MethodSource("shouldChunkProcessInstanceBatchArguments")
  void shouldChunkProcessInstanceBatch(final int chunkSize, final List<ArchiveBatch> expected) {
    final var batch = new ArchiveBatch("finished-date", List.of("1", "2", "3"));

    assertThat(batch.chunk(chunkSize)).isEqualTo(expected);
  }

  private static Stream<Arguments> shouldChunkProcessInstanceBatchArguments() {
    return Stream.of(
        Arguments.of(5, List.of(new ArchiveBatch("finished-date", List.of("1", "2", "3")))),
        Arguments.of(3, List.of(new ArchiveBatch("finished-date", List.of("1", "2", "3")))),
        Arguments.of(
            2,
            List.of(
                new ArchiveBatch("finished-date", List.of("1", "2")),
                new ArchiveBatch("finished-date", List.of("3")))),
        Arguments.of(
            1,
            List.of(
                new ArchiveBatch("finished-date", List.of("1")),
                new ArchiveBatch("finished-date", List.of("2")),
                new ArchiveBatch("finished-date", List.of("3")))));
  }
}
