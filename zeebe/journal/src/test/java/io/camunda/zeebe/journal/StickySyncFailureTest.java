/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class StickySyncFailureTest {

  private final StickySyncFailure syncFailure = new StickySyncFailure();

  @Test
  void shouldKeepFirstFailure() {
    // given
    final var first = new IOException("first");
    syncFailure.record(first);

    // when
    syncFailure.record(new IOException("second"));

    // then
    assertThat(syncFailure.get()).isSameAs(first);
  }

  @Test
  void shouldNotifyListenerOfFirstFailureOnly() {
    // given
    final var notified = new ArrayList<Throwable>();
    syncFailure.setListener(notified::add);
    final var first = new IOException("first");

    // when
    syncFailure.record(first);
    syncFailure.record(new IOException("second"));

    // then
    assertThat(notified).containsExactly(first);
  }

  @Test
  void shouldNotifyLateListenerOfEarlierFailure() {
    // given - e.g. a failure in the background before the listener is set
    final var failure = new IOException("failure");
    syncFailure.record(failure);
    final var notified = new ArrayList<Throwable>();

    // when
    syncFailure.setListener(notified::add);

    // then
    assertThat(notified).containsExactly(failure);
  }
}
