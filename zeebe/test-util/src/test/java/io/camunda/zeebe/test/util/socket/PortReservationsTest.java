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
  private static final int MIN = 20_000;

  @TempDir private Path lockDir;

  @Test
  void shouldReleaseAllPortsWhenClosed() {
    // given - an allocator with exactly as many ports as are reserved
    final var allocator = new PortAllocator(lockDir, MIN, MIN + 2);
    final var ports = new PortReservations(allocator::reserve);
    ports.nextPort();
    ports.nextPort();
    assertThatThrownBy(allocator::reserve).isInstanceOf(IllegalStateException.class);

    // when
    ports.close();

    // then
    assertThat(allocator.reserve().port()).isBetween(MIN, MIN + 1);
    assertThat(allocator.reserve().port()).isBetween(MIN, MIN + 1);
  }

  @Test
  void shouldAllowClosingTwice() {
    // given
    final var ports = new PortReservations(new PortAllocator(lockDir, MIN, MIN + 1)::reserve);
    ports.nextPort();
    ports.close();

    // when - then
    ports.close();
  }
}
