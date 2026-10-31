/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code isEmpty()} / {@code hasEmptyOrFilter()} contract shared by every {@link
 * OrFilter} implementation. Whether {@code isEmpty()} itself references every criteria record
 * component is checked structurally by {@code OrFilterIsEmptyCoverageArchTest} in the
 * archunit-tests module.
 */
class OrFilterTest {

  @Test
  void flowNodeInstanceFilterIsEmptyOnlyWithoutCriteria() {
    assertThat(new FlowNodeInstanceFilter.Builder().build().isEmpty()).isTrue();
    assertThat(new FlowNodeInstanceFilter.Builder().tenantIds("tenant").build().isEmpty())
        .isFalse();
  }

  @Test
  void processInstanceFilterIsEmptyOnlyWithoutCriteria() {
    assertThat(new ProcessInstanceFilter.Builder().build().isEmpty()).isTrue();
    assertThat(new ProcessInstanceFilter.Builder().tenantIds("tenant").build().isEmpty()).isFalse();
  }

  @Test
  void userTaskFilterIsEmptyOnlyWithoutCriteria() {
    assertThat(new UserTaskFilter.Builder().build().isEmpty()).isTrue();
    assertThat(new UserTaskFilter.Builder().tenantIds("tenant").build().isEmpty()).isFalse();
  }

  @Test
  void processDefinitionStatisticsFilterIsEmptyIgnoringProcessDefinitionKey() {
    // processDefinitionKey is the mandatory join key shared by every $or group, not a filter
    // criterion, so a filter carrying only that field must still be considered empty.
    assertThat(new ProcessDefinitionStatisticsFilter.Builder(123L).build().isEmpty()).isTrue();
    assertThat(
            new ProcessDefinitionStatisticsFilter.Builder(123L).states("ACTIVE").build().isEmpty())
        .isFalse();
  }

  @Test
  void hasEmptyOrFilterDetectsAGroupWithNoCriteria() {
    final var withEmptyGroup =
        new ProcessInstanceFilter.Builder()
            .orFilters(List.of(new ProcessInstanceFilter.Builder().build()))
            .build();
    final var withoutEmptyGroup =
        new ProcessInstanceFilter.Builder()
            .orFilters(List.of(new ProcessInstanceFilter.Builder().states("ACTIVE").build()))
            .build();

    assertThat(withEmptyGroup.hasEmptyOrFilter()).isTrue();
    assertThat(withoutEmptyGroup.hasEmptyOrFilter()).isFalse();
  }
}
