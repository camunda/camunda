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
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.SortOrder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClusterVariableSortTest extends AbstractSortTransformerTest {

  @Test
  public void shouldApplySortConditionByValueASC() {
    // given
    final var clusterVariableFilter = FilterBuilders.clusterVariable((f) -> f.names("foo"));
    final var request =
        SearchQueryBuilders.clusterVariableSearchQuery(
            (q) -> q.filter(clusterVariableFilter).sort((s) -> s.value().asc()).page(p -> p));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    // Name being default, we have 2 sort conditions
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
    final var clusterVariableFilter = FilterBuilders.clusterVariable((f) -> f.names("bar"));
    final var request =
        SearchQueryBuilders.clusterVariableSearchQuery(
            (q) -> q.filter(clusterVariableFilter).sort((s) -> s.name().desc()).page(p -> p));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    // Name being default, we have 2 sort conditions for the name, ASC and DESC
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByNameConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("name") && s.field().order().equals(SortOrder.DESC));

    assertThat(sortByNameConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByScopeASC() {
    // given
    final var clusterVariableFilter = FilterBuilders.clusterVariable((f) -> f.names("scope"));
    final var request =
        SearchQueryBuilders.clusterVariableSearchQuery(
            (q) -> q.filter(clusterVariableFilter).sort((s) -> s.scope().asc()).page(p -> p));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    // Name being default, we have 2 sort conditions
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByScopeConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("scope") && s.field().order().equals(SortOrder.ASC));

    assertThat(sortByScopeConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByTenantIdDESC() {
    // given
    final var clusterVariableFilter = FilterBuilders.clusterVariable((f) -> f.names("tenantId"));
    final var request =
        SearchQueryBuilders.clusterVariableSearchQuery(
            (q) -> q.filter(clusterVariableFilter).sort((s) -> s.tenantId().desc()).page(p -> p));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    // Name being default, we have 2 sort conditions
    Assertions.assertThat(sort).hasSize(2);

    final boolean sortByTenantIdConditionCheck =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("tenantId")
                        && s.field().order().equals(SortOrder.DESC));

    assertThat(sortByTenantIdConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByNameAndValueASC() {
    // given
    final var clusterVariableFilter = FilterBuilders.clusterVariable((f) -> f.names("multi"));
    final var request =
        SearchQueryBuilders.clusterVariableSearchQuery(
            (q) ->
                q.filter(clusterVariableFilter)
                    .sort((s) -> s.name().asc().value().asc())
                    .page(p -> p));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    // Name being default, we have 2 sort conditions + 1 default = 3
    Assertions.assertThat(sort).hasSize(3);

    final boolean sortByNameConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("name") && s.field().order().equals(SortOrder.ASC));

    final boolean sortByValueConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("value") && s.field().order().equals(SortOrder.ASC));

    assertThat(sortByNameConditionCheck).isTrue();
    assertThat(sortByValueConditionCheck).isTrue();
  }

  @Test
  public void shouldApplySortConditionByScopeAndTenantIdMixedOrder() {
    // given
    final var clusterVariableFilter = FilterBuilders.clusterVariable((f) -> f.names("mixed"));
    final var request =
        SearchQueryBuilders.clusterVariableSearchQuery(
            (q) ->
                q.filter(clusterVariableFilter)
                    .sort((s) -> s.scope().asc().tenantId().desc())
                    .page(p -> p));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    // Name being default, we have 3 sort conditions
    Assertions.assertThat(sort).hasSize(3);

    final boolean sortByScopeConditionCheck =
        sort.stream()
            .anyMatch(
                s -> s.field().field().equals("scope") && s.field().order().equals(SortOrder.ASC));

    final boolean sortByTenantIdConditionCheck =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("tenantId")
                        && s.field().order().equals(SortOrder.DESC));

    assertThat(sortByScopeConditionCheck).isTrue();
    assertThat(sortByTenantIdConditionCheck).isTrue();
  }
}
