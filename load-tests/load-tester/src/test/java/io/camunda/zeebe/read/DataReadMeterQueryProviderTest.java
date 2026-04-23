/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.read.DataReadMeter.ReadQuery;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DataReadMeterQueryProviderTest {

  @Test
  void shouldReturnAllQueriesWhenNoneDisabled() {
    // given
    final var allQueries = DataReadMeterQueryProvider.getDefaultQueries();

    // when
    final var result = DataReadMeterQueryProvider.getDefaultQueries(Set.of());

    // then
    assertThat(result).hasSameSizeAs(allQueries);
  }

  @Test
  void shouldFilterOutDisabledQueries() {
    // given
    final var disabled = Set.of("process_instances_active", "audit_log_by_category");

    // when
    final var result = DataReadMeterQueryProvider.getDefaultQueries(disabled);

    // then
    assertThat(result)
        .extracting(ReadQuery::name)
        .doesNotContainAnyElementsOf(disabled)
        .isNotEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAllQueriesDisabled() {
    // given
    final var allNames =
        DataReadMeterQueryProvider.getDefaultQueries().stream()
            .map(ReadQuery::name)
            .collect(java.util.stream.Collectors.toSet());

    // when
    final var result = DataReadMeterQueryProvider.getDefaultQueries(allNames);

    // then
    assertThat(result).isEmpty();
  }
}
