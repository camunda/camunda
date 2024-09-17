/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.SortOrder;
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
}
