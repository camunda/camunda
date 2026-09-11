/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PortRangeTest {
  @Test
  void shouldAllocateEphemeralPortsForLargeWorkerIds() {
    final var ports =
        IntStream.range(0, 100)
            .mapToObj(workerId -> new PortRange("localhost", workerId + 1_000).next().getPort())
            .toList();

    assertThat(ports).allMatch(port -> port > 0 && port <= 65_535);
  }

  @Test
  void shouldAllocateEphemeralPortsForConcurrentWorkers() {
    final var ports =
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(workerId -> new PortRange("localhost", workerId + 2_000).next().getPort())
            .toList();

    assertThat(ports).allMatch(port -> port > 0 && port <= 65_535);
  }
}
