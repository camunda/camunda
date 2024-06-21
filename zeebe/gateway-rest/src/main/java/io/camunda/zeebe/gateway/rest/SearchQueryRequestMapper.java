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
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.service.search.sort.UserTaskSort;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableValueFilterRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.springframework.http.ProblemDetail;

public final class SearchQueryRequestMapper {

  private SearchQueryRequestMapper() {}

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final ProcessInstanceSearchQueryRequest request) {
    final var page = toSearchQueryPage(request.getPage()).get();
    final var sorting = toSearchQuerySort(request.getSort()).get();
    final var processInstanceFilter = toProcessInstanceFilter(request.getFilter()).get();
    return Either.right(
        SearchQueryBuilders.processInstanceSearchQuery()
            .page(page)
            .filter(processInstanceFilter)
            .sort(sorting)
            .build());
  }

  // implement method toUserTaskQuery
  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final UserTaskSearchQueryRequest request) {
    final var page = toSearchQueryPage(request.getPage()).get();
    final var sorting = toUserTaskSearchQuerySort(request.getSort()).get();
    final var userTaskFilter = toUserTaskFilter(request.getFilter()).get();
    return Either.right(
        SearchQueryBuilders.userTaskSearchQuery()
            .page(page)
            .filter(userTaskFilter)
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

  public static Either<ProblemDetail, UserTaskFilter> toUserTaskFilter(
      final UserTaskFilterRequest filter) {
    final var builder = FilterBuilders.userTask();

    if (filter != null) {
      // key
      if (filter.getKey() != null && !filter.getKey().isEmpty()) {
        builder.userTaskKeys(filter.getKey());
      }

      // state
      if (filter.getState() != null && !filter.getState().isEmpty()) {
        builder.userTaskState(filter.getState());
      }

      // assignee
      if (filter.getAssignee() != null && !filter.getAssignee().isEmpty()) {
        builder.assignees(filter.getAssignee());
      }

      // candidateGroup
      if (filter.getCandidateGroup() != null && !filter.getCandidateGroup().isEmpty()) {
        builder.candidateGroups(filter.getCandidateGroup());
      }

      // candidateUser
      if (filter.getCandidateUser() != null && !filter.getCandidateUser().isEmpty()) {
        builder.candidateUsers(filter.getCandidateUser());
      }

      // processDefinitionKey
      if (filter.getProcessDefinitionKey() != null && !filter.getProcessDefinitionKey().isEmpty()) {
        builder.processDefinitionKeys(filter.getProcessDefinitionKey().toString());
      }

      // processInstanceKey
      if (filter.getProcessInstanceKey() != null && !filter.getProcessInstanceKey().isEmpty()) {
        builder.processInstanceKeys(filter.getProcessInstanceKey());
      }

      // tenantIds
      if (filter.getTenantIds() != null && !filter.getTenantIds().isEmpty()) {
        builder.tenantIds(filter.getTenantIds());
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
                  p.size(requestedPage.getSize())
                      .from(requestedPage.getFrom())
                      .searchAfter(toArrayOrNull(requestedPage.getSearchAfter()))
                      .searchBefore(toArrayOrNull(requestedPage.getSearchBefore()))));
    }

    return Either.right(null);
  }

  public static Either<ProblemDetail, ProcessInstanceSort> toSearchQuerySort(
      final List<SearchQuerySortRequest> sorting) {
    if (sorting != null && !sorting.isEmpty()) {
      final var builder = SortOptionBuilders.processInstance();

      for (final SearchQuerySortRequest sort : sorting) {
        final var field = sort.getField();
        final var order = sort.getOrder();

        if ("processInstanceKey".equals(field)) {
          builder.processInstanceKey();
        } else if ("startDate".equals(field)) {
          builder.startDate();
        } else if ("endDate".equals(field)) {
          builder.endDate();
        } else {
          throw new RuntimeException("unkown sortBy " + field);
        }

        if ("asc".equalsIgnoreCase(order)) {
          builder.asc();
        } else if ("desc".equalsIgnoreCase(order)) {
          builder.desc();
        } else {
          throw new RuntimeException("unkown sortOrder " + order);
        }
      }

      return Either.right(builder.build());
    }

    return Either.right(null);
  }

  public static Either<ProblemDetail, UserTaskSort> toUserTaskSearchQuerySort(
      final List<SearchQuerySortRequest> sorting) {
    if (sorting != null && !sorting.isEmpty()) {
      final var builder = SortOptionBuilders.userTask();

      for (final SearchQuerySortRequest sort : sorting) {
        final var field = sort.getField();
        final var order = sort.getOrder();

        if ("creationTime".equals(field)) {
          builder.creationDate();
        } else if ("completionTime".equals(field)) {
          builder.completionDate();
        } else {
          throw new RuntimeException("unkown sortBy " + field);
        }

        if ("asc".equalsIgnoreCase(order)) {
          builder.asc();
        } else if ("desc".equalsIgnoreCase(order)) {
          builder.desc();
        } else {
          throw new RuntimeException("unkown sortOrder " + order);
        }
      }

      return Either.right(builder.build());
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
