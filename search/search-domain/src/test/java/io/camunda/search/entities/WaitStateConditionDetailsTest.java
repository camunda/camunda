/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaitStateConditionDetailsTest {

  @Test
  void shouldReturnEmptyListWhenEventsIsNull() {
    // given / when
    final var details = new WaitStateConditionDetails(null, null);

    // then
    assertThat(details.events()).isNotNull().isEmpty();
  }

  @Test
  void shouldReturnMutableListWhenEventsIsNull() {
    // given
    final var details = new WaitStateConditionDetails(null, null);

    // when
    details.events().add("event");

    // then
    assertThat(details.events()).containsExactly("event");
  }

  @Test
  void shouldReturnCopyOfProvidedEvents() {
    // given
    final var mutableList = new ArrayList<>(List.of("event-a", "event-b"));
    final var details = new WaitStateConditionDetails(null, mutableList);

    // when
    mutableList.add("event-c");

    // then
    assertThat(details.events()).containsExactly("event-a", "event-b");
  }

  @Test
  void shouldReturnMutableListWhenEventsIsNonNull() {
    // given
    final var details = new WaitStateConditionDetails(null, List.of("event-a"));

    // when
    details.events().add("event-b");

    // then
    assertThat(details.events()).containsExactly("event-a", "event-b");
  }
}
