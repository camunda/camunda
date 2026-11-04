/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FilterByKeysQueryTest {
  @Test
  void shouldDefaultToEmptyInclusionFilters() {
    // given
    final var filter = new FilterByKeysQuery(Map.of("processInstanceKey", List.of("1")));

    // when/then
    assertThat(filter.inclusionFilters()).isEmpty();
  }

  @Test
  void shouldDefaultToEmptyExclusionFilters() {
    // given
    final var filter = new FilterByKeysQuery(Map.of("processInstanceKey", List.of("1")));

    // when/then
    assertThat(filter.exclusionFilters()).isEmpty();
  }

  @Test
  void shouldCreateKeysByFieldWhenUsingSingleFieldConstructor() {
    // given
    final var filter = new FilterByKeysQuery("processInstanceKey", List.of("1"));

    // when/then
    assertThat(filter.keysByField()).isEqualTo(Map.of("processInstanceKey", List.of("1")));
  }

  @Test
  void shouldAddInclusionFilter() {
    // given
    final var filter = new FilterByKeysQuery("processInstanceKey", List.of("1"));

    // when
    final var newFilter = filter.inclusion("joinRelation", "processInstance");

    // then
    assertThat(newFilter.inclusionFilters()).isEqualTo(Map.of("joinRelation", "processInstance"));
  }

  @Test
  void shouldAddExclusionFilter() {
    // given
    final var filter = new FilterByKeysQuery("processInstanceKey", List.of("1"));

    // when
    final var newFilter = filter.exclusion("joinRelation", "processInstance");

    // then
    assertThat(newFilter.exclusionFilters()).isEqualTo(Map.of("joinRelation", "processInstance"));
  }
}
