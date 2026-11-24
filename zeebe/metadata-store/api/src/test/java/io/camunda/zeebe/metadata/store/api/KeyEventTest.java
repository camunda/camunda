/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.metadata.store.api.KeyEvent.ChangeType;
import org.junit.jupiter.api.Test;

/**
 * A consumer reads current and previous by the change type, so an event whose values contradict its
 * type must never reach it.
 */
final class KeyEventTest {

  private static final StoreKey KEY = StoreKey.of(Namespace.cluster("config"), "a.b");
  private static final Value VALUE = Value.of(new byte[] {1});

  @Test
  void shouldAcceptEventsMatchingTheirType() {
    // when - then
    assertThatCode(() -> KeyEvent.added(KEY, VALUE)).doesNotThrowAnyException();
    assertThatCode(() -> KeyEvent.replaced(KEY, VALUE, VALUE)).doesNotThrowAnyException();
    assertThatCode(() -> KeyEvent.removed(KEY, VALUE)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectAddWithPrevious() {
    // when - then
    assertThatThrownBy(() -> new KeyEvent(ChangeType.ADD, KEY, VALUE, VALUE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectReplaceWithoutPrevious() {
    // when - then
    assertThatThrownBy(() -> new KeyEvent(ChangeType.REPLACE, KEY, VALUE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectRemoveWithCurrent() {
    // when - then
    assertThatThrownBy(() -> new KeyEvent(ChangeType.REMOVE, KEY, VALUE, VALUE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
