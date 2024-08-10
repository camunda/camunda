/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkipPositionsFilterTest {
  @Test
  void shouldSkipEventWithMatchingPosition() {
    // given
    final var filter = SkipPositionsFilter.of(Set.of(1L, 2L, 3L));
    final var event = mock(LoggedEventImpl.class);

    // when
    when(event.getPosition()).thenReturn(2L);

    // then
    assertThat(filter.applies(event)).isFalse();
  }

  @Test
  void shouldNotSkipEventWithNonMatchingPosition() {
    // given
    final var filter = SkipPositionsFilter.of(Set.of(1L, 2L, 3L));
    final var event = mock(LoggedEventImpl.class);

    // when
    when(event.getPosition()).thenReturn(4L);

    // then
    assertThat(filter.applies(event)).isTrue();
  }

  @Test
  void shouldNotSkipEventWhenSetIsEmpty() {
    // given
    final var filter = SkipPositionsFilter.of(Set.of());
    final var event = mock(LoggedEventImpl.class);

    // when
    when(event.getPosition()).thenReturn(4L);

    // then
    assertThat(filter.applies(event)).isTrue();
  }
}
