/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static java.time.ZoneOffset.UTC;

import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.search.sort.UsageMetricsSort;
import io.camunda.search.sort.UsageMetricsSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UsageMetricsSortTest extends AbstractSortTransformerTest {
  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("id", SortOrder.ASC, s -> s.id().asc()),
        new TestArguments("event", SortOrder.ASC, s -> s.event().asc()),
        new TestArguments("eventTime", SortOrder.ASC, s -> s.eventTime().asc()),
        new TestArguments("value", SortOrder.ASC, s -> s.value().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<Builder, ObjectBuilder<UsageMetricsSort>> fn) {
    // when
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 2, 0, 0, 0, 0, UTC);
    final var filter =
        new UsageMetricsFilter.Builder()
            .startTime(startTime)
            .endTime(endTime)
            .events("sort-event")
            .build();
    final var request = SearchQueryBuilders.usageMetricsSearchQuery(q -> q.filter(filter).sort(fn));
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).hasSize(2);
    Assertions.assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              Assertions.assertThat(t.field().field()).isEqualTo(field);
              Assertions.assertThat(t.field().order()).isEqualTo(sortOrder);
            });
    Assertions.assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              Assertions.assertThat(t.field().field()).isEqualTo("id");
              Assertions.assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  private record TestArguments(
      String field,
      SortOrder sortOrder,
      Function<UsageMetricsSort.Builder, ObjectBuilder<UsageMetricsSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
