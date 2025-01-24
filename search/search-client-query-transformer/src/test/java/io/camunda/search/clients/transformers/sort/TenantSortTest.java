/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.search.sort.TenantSort;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TenantSortTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("tenantId", SortOrder.DESC, s -> s.tenantId().desc()),
        new TestArguments("name", SortOrder.ASC, s -> s.name().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<TenantSort.Builder, ObjectBuilder<TenantSort>> fn) {

    // when
    final var request = SearchQueryBuilders.tenantSearchQuery(q -> q.sort(fn));
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(2); // Primary field and default "key" sort
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(field);
              assertThat(t.field().order()).isEqualTo(sortOrder);
            });
    assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo("key"); // Default sorting
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC); // Default order
            });
  }

  private record TestArguments(
      String field, SortOrder sortOrder, Function<TenantSort.Builder, ObjectBuilder<TenantSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
