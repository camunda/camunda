/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.SortOrder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class VariableSortTest extends AbstractSortTransformerTest {

  @Test
  public void shouldApplySortConditionByValueASC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("foo").build()));
    final var request =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.value().asc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByValueConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("value") && s.field().order().equals(SortOrder.ASC));

    assertThat(sortByValueConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByNameDESC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("bar").build()));
    final var request =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.name().desc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByNameConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("name") && s.field().order().equals(SortOrder.DESC));

    assertThat(sortByNameConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByTenantIdASC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("tenant").build()));
    final var request =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.tenantId().asc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByTenantIdConditionCheck =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("tenantId")
                        && s.field().order().equals(SortOrder.ASC));

    assertThat(sortByTenantIdConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByVariableKeyDESC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("varKey").build()));
    final var request =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.variableKey().desc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByVariableKeyConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("key") && s.field().order().equals(SortOrder.DESC));

    assertThat(sortByVariableKeyConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByScopeKeyASC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("scope").build()));
    final var request =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.scopeKey().asc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByScopeKeyConditionCheck =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("scopeKey")
                        && s.field().order().equals(SortOrder.ASC));

    assertThat(sortByScopeKeyConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByProcessInstanceKeyDESC() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (f) -> f.variable(new VariableValueFilter.Builder().name("processInstance").build()));
    final var request =
        SearchQueryBuilders.variableSearchQuery(
            (q) -> q.filter(variableFilter).sort((s) -> s.processInstanceKey().desc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByProcessInstanceKeyConditionCheck =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("processInstanceKey")
                        && s.field().order().equals(SortOrder.DESC));

    assertThat(sortByProcessInstanceKeyConditionCheck).isTrue();
  }
}
