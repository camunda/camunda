/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.search.sort.SortOption.AbstractBuilder;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableValueFilterRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.springframework.http.ProblemDetail;

public final class SearchQueryRequestMapper {

  private SearchQueryRequestMapper() {}

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final ProcessInstanceSearchQueryRequest request) {
    final var page = toSearchQueryPage(request.getPage()).get();
    final var sorting =
        toSearchQuerySort(
                request.getSort(),
                SortOptionBuilders::processInstance,
                SearchQueryRequestMapper::applyProcessInstanceSortField)
            .get();
    final var processInstanceFilter = toProcessInstanceFilter(request.getFilter()).get();
    return Either.right(
        SearchQueryBuilders.processInstanceSearchQuery()
            .page(page)
            .filter(processInstanceFilter)
            .sort(sorting)
            .build());
  }

  public static Either<ProblemDetail, ProcessInstanceFilter> toProcessInstanceFilter(
      final ProcessInstanceFilterRequest filter) {
    final var builder = FilterBuilders.processInstance();

    if (filter != null) {
      final var variableFilters = toVariableValueFilter(filter.getVariables()).get();
      if (variableFilters != null) {
        builder.variable(variableFilters);
      }
      if (filter.getKey() != null && !filter.getKey().isEmpty()) {
        builder.processInstanceKeys(filter.getKey());
      }
    }

    return Either.right(builder.build());
  }

  public static Either<ProblemDetail, List<VariableValueFilter>> toVariableValueFilter(
      final List<VariableValueFilterRequest> filters) {

    final List<VariableValueFilter> result;

    if (filters != null && !filters.isEmpty()) {
      result =
          filters.stream()
              .map(SearchQueryRequestMapper::toVariableValueFilter)
              .map(Either::get)
              .toList();
    } else {
      result = null;
    }

    return Either.right(result);
  }

  public static Either<ProblemDetail, VariableValueFilter> toVariableValueFilter(
      final VariableValueFilterRequest f) {
    return Either.right(
        FilterBuilders.variableValue(
            (v) ->
                v.name(f.getName())
                    .eq(f.getEq())
                    .gt(f.getGt())
                    .gte(f.getGte())
                    .lt(f.getLt())
                    .lte(f.getLte())));
  }

  public static Either<ProblemDetail, SearchQueryPage> toSearchQueryPage(
      final SearchQueryPageRequest requestedPage) {
    if (requestedPage != null) {
      return Either.right(
          SearchQueryPage.of(
              (p) ->
                  p.size(requestedPage.getLimit())
                      .from(requestedPage.getFrom())
                      .searchAfter(toArrayOrNull(requestedPage.getSearchAfter()))
                      .searchBefore(toArrayOrNull(requestedPage.getSearchBefore()))));
    }

    return Either.right(null);
  }

  private static <T, B extends AbstractBuilder<B> & ObjectBuilder<T>>
      Either<ProblemDetail, T> toSearchQuerySort(
          final List<SearchQuerySortRequest> sorting,
          final Supplier<B> builderSupplier,
          BiConsumer<String, B> sortFieldConsumer) {
    if (sorting != null && !sorting.isEmpty()) {
      final var builder = builderSupplier.get();
      for (SearchQuerySortRequest sort : sorting) {
        if (sort.getField() == null) {
          throw new IllegalArgumentException("Sort field must not be null");
        }
        if (sort.getOrder() == null) {
          throw new IllegalArgumentException("Sort order must not be null");
        }
        sortFieldConsumer.accept(sort.getField(), builder);
        applySortOrder(sort.getOrder(), builder);
      }

      return Either.right(builder.build());
    }

    return Either.right(null);
  }

  private static void applyProcessInstanceSortField(
      final String field, final ProcessInstanceSort.Builder builder) {
    switch (field) {
      case "processInstanceKey" -> builder.processInstanceKey();
      case "startDate" -> builder.startDate();
      case "endDate" -> builder.endDate();
      default -> throw new IllegalArgumentException("Unknown sortBy: " + field);
    }
  }

  private static void applySortOrder(final String order, final AbstractBuilder<?> builder) {
    switch (order.toLowerCase()) {
      case "asc" -> builder.asc();
      case "desc" -> builder.desc();
      default -> throw new IllegalArgumentException("Unknown sortOrder " + order);
    }
  }

  private static Object[] toArrayOrNull(final List<Object> values) {
    if (values == null || values.isEmpty()) {
      return null;
    } else {
      return values.toArray();
    }
  }
}
