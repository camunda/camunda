/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.AgentInstanceHistorySort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentHistoryFieldSortingTransformerTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments(AgentHistoryTemplate.KEY, SortOrder.DESC, s -> s.historyItemKey().desc()),
        new TestArguments(AgentHistoryTemplate.ITERATION, SortOrder.ASC, s -> s.iteration().asc()),
        new TestArguments(
            AgentHistoryTemplate.PRODUCED_AT, SortOrder.DESC, s -> s.producedAt().desc()));
  }

  @ParameterizedTest(name = "should sort by {0} in ''{1}'' direction")
  @MethodSource("provideSortParameters")
  void shouldSortByField(
      final String expectedField,
      final SortOrder sortOrder,
      final Function<AgentInstanceHistorySort.Builder, ObjectBuilder<AgentInstanceHistorySort>>
          fn) {
    // given
    final var request = SearchQueryBuilders.agentInstanceHistorySearchQuery(q -> q.sort(fn));

    // when
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(2);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(expectedField);
              assertThat(t.field().order()).isEqualTo(sortOrder);
            });
    assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(AgentHistoryTemplate.KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldUseHistoryItemKeyAsDefaultTiebreakerWhenNoSortSpecified() {
    // given — query with no explicit sort
    final var request = SearchQueryBuilders.agentInstanceHistorySearchQuery(q -> q);

    // when
    final var sort = transformRequest(request);

    // then — only the implicit default tiebreaker is appended
    assertThat(sort).hasSize(1);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(AgentHistoryTemplate.KEY);
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldThrowForUnknownSortField() {
    // given
    final var transformer = new AgentHistoryFieldSortingTransformer();

    // when / then
    assertThatThrownBy(() -> transformer.apply("unknownField"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknownField");
  }

  private record TestArguments(
      String expectedField,
      SortOrder sortOrder,
      Function<AgentInstanceHistorySort.Builder, ObjectBuilder<AgentInstanceHistorySort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {expectedField, sortOrder, fn};
    }

    @Override
    public String toString() {
      return expectedField + " " + sortOrder;
    }
  }
}
