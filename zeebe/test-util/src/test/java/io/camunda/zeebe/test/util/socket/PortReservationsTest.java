/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PortReservationsTest {
  // wide enough that ports in use by other processes on the machine cannot exhaust the range
  private static final int MIN = 20_000;
  private static final int MAX = MIN + 1_000;

  @TempDir private Path lockDir;

  @Test
  void shouldReleaseAllPortsWhenClosed() {
    // given - reserved ports that a single-port allocator cannot reserve
    final var ports = new PortReservations(new PortAllocator(lockDir, MIN, MAX)::reserve);
    final int firstPort = ports.nextPort();
    final int secondPort = ports.nextPort();
    final var first = new PortAllocator(lockDir, firstPort, firstPort + 1);
    final var second = new PortAllocator(lockDir, secondPort, secondPort + 1);
    assertThatThrownBy(first::reserve).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(second::reserve).isInstanceOf(IllegalStateException.class);

    // when
    ports.close();

    // then
    assertThat(first.reserve().port()).isEqualTo(firstPort);
    assertThat(second.reserve().port()).isEqualTo(secondPort);
  }

  @Test
  void shouldAllowClosingTwice() {
    // given
    final var ports = new PortReservations(new PortAllocator(lockDir, MIN, MAX)::reserve);
    ports.nextPort();
    ports.close();

    // when - then
    ports.close();
  }
}
