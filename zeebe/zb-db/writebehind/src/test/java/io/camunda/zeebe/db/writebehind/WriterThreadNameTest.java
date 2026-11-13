/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class WriterThreadNameTest {

  @Test
  void shouldNameWriterThreadsAfterTheirStore() {
    // given
    final var storage = new InMemoryRawDbAccess();
    final var config = WriteBehindConfiguration.defaults();

    // when
    try (final var first = new WriteBehindStore<>(new RawBackend(storage), config, "partition-1");
        final var second = new WriteBehindStore<>(new RawBackend(storage), config, "partition-2")) {

      // then
      assertThat(threadNames())
          .contains("zb-db-write-behind-partition-1", "zb-db-write-behind-partition-2");
    }
  }

  private static Set<String> threadNames() {
    return Thread.getAllStackTraces().keySet().stream()
        .map(Thread::getName)
        .collect(Collectors.toSet());
  }
}
