/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.query.SearchQuery;
import io.camunda.service.query.filter.FilterBuilders;
import io.camunda.service.query.filter.ProcessInstanceFilter;
import io.camunda.service.query.filter.VariableValueFilter;
import io.camunda.service.query.types.SearchQueryPage;
import io.camunda.service.query.types.SearchQuerySort;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.springframework.http.ProblemDetail;

public class SearchQueryRequestMapper {

  public static Either<ProblemDetail, SearchQuery<ProcessInstanceFilter>> toProcessInstanceQuery(
      final ProcessInstanceSearchQueryRequest request) {
    final var page = toSearchQueryPage(request.getPage()).get();
    final var sorting = toSearchQuerySort(request.getSort()).get();
    final var processInstanceFilter = toProcessInstanceFilter(request.getFilter()).get();
    return Either.right(
        SearchQuery.of((s) -> s.page(page).sort(sorting).filter(processInstanceFilter)));
  }

  public static Either<ProblemDetail, ProcessInstanceFilter> toProcessInstanceFilter(
      final io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilter filter) {
    final var variableFilters = toVariableValueFilter(filter.getVariables()).get();
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            (f) -> f.variable(variableFilters).processInstanceKeys(filter.getKey()));
    return Either.right(processInstanceFilter);
  }

  public static Either<ProblemDetail, List<VariableValueFilter>> toVariableValueFilter(
      final List<io.camunda.zeebe.gateway.protocol.rest.VariableValueFilter> filters) {

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
      final io.camunda.zeebe.gateway.protocol.rest.VariableValueFilter f) {
    return Either.right(
        FilterBuilders.variable(
            (v) ->
                v.name(f.getName())
                    .eq(f.getEq())
                    .gt(f.getEq())
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
                  p.size(requestedPage.getSize())
                      .from(requestedPage.getFrom())
                      .searchAfter(toArrayOrNull(requestedPage.getSearchAfter()))
                      .searchAfterOrEqual(toArrayOrNull(requestedPage.getSearchAfterOrEqual()))
                      .searchBefore(toArrayOrNull(requestedPage.getSearchBefore()))
                      .searchBeforeOrEqual(toArrayOrNull(requestedPage.getSearchBeforeOrEqual()))));
    }

    return Either.right(null);
  }

  public static Either<ProblemDetail, SearchQuerySort> toSearchQuerySort(
      final SearchQuerySortRequest sorting) {
    if (sorting != null) {
      return Either.right(
          SearchQuerySort.of((s) -> s.field(sorting.getField()).order(sorting.getOrder())));
    }

    return Either.right(null);
  }

  private static Object[] toArrayOrNull(final List<Object> values) {
    if (values == null || values.isEmpty()) {
      return null;
    } else {
      return values.toArray();
    }
  }
}
