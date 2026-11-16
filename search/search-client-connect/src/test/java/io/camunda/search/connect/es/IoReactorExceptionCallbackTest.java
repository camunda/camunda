/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import org.junit.jupiter.api.Test;

final class IoReactorExceptionCallbackTest {

  @Test
  void shouldTreatClosedSelectorExceptionAsShutdownRace() {
    // given
    final var exception = new ClosedSelectorException();

    // when - then
    assertThat(IoReactorExceptionCallback.isShutdownRace(exception)).isTrue();
  }

  @Test
  void shouldTreatMissingPoolEntryAsShutdownRace() {
    // given
    final var exception =
        new IllegalStateException(IoReactorExceptionCallback.POOL_ENTRY_MISSING_MESSAGE);

    // when - then
    assertThat(IoReactorExceptionCallback.isShutdownRace(exception)).isTrue();
  }

  @Test
  void shouldNotTreatOtherExceptionsAsShutdownRace() {
    // given
    final var otherState = new IllegalStateException("something else");
    final var other = new IOException("boom");

    // when - then
    assertThat(IoReactorExceptionCallback.isShutdownRace(otherState)).isFalse();
    assertThat(IoReactorExceptionCallback.isShutdownRace(other)).isFalse();
  }
}
