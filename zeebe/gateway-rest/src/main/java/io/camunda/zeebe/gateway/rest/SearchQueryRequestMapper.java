/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_SEARCH_BEFORE_AND_AFTER;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_SORT_FIELD_MUST_NOT_BE_NULL;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_UNKNOWN_SORT_BY;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_UNKNOWN_SORT_ORDER;
import static java.util.Optional.ofNullable;

import io.camunda.service.search.filter.DecisionDefinitionFilter;
import io.camunda.service.search.filter.DecisionRequirementsFilter;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.IncidentFilter;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.IncidentQuery;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.TypedSearchQueryBuilder;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.sort.DecisionDefinitionSort;
import io.camunda.service.search.sort.DecisionRequirementsSort;
import io.camunda.service.search.sort.IncidentSort;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.search.sort.SortOption;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.service.search.sort.UserTaskSort;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableValueFilterRequest;
import io.camunda.zeebe.gateway.rest.validator.RequestValidator;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.springframework.http.ProblemDetail;

public final class SearchQueryRequestMapper {

  private SearchQueryRequestMapper() {}

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final ProcessInstanceSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::processInstance,
            SearchQueryRequestMapper::applyProcessInstanceSortField);
    final var filter = toProcessInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQuery(
      final DecisionDefinitionSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::decisionDefinition,
            SearchQueryRequestMapper::applyDecisionDefinitionSortField);
    final var filter = toDecisionDefinitionFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQuery(
      final DecisionRequirementsSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::decisionRequirements,
            SearchQueryRequestMapper::applyDecisionRequirementsSortField);
    final var filter = toDecisionRequirementsFilter(request.getFilter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionRequirementsSearchQuery);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final UserTaskSearchQueryRequest request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::userTask,
            SearchQueryRequestMapper::applyUserTaskSortField);
    final var filter = toUserTaskFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final IncidentSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::incident,
            SearchQueryRequestMapper::applyIncidentSortField);
    final var filter = toIncidentFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  private static ProcessInstanceFilter toProcessInstanceFilter(
      final ProcessInstanceFilterRequest filter) {
    final var builder = FilterBuilders.processInstance();

    if (filter != null) {
      final var variableFilters = toVariableValueFilter(filter.getVariables());
      if (variableFilters != null) {
        builder.variable(variableFilters);
      }
      if (filter.getKey() != null && !filter.getKey().isEmpty()) {
        builder.processInstanceKeys(filter.getKey());
      }
    }

    return builder.build();
  }

  private static DecisionDefinitionFilter toDecisionDefinitionFilter(
      final DecisionDefinitionFilterRequest filter) {
    final var builder = FilterBuilders.decisionDefinition();

    if (filter != null) {
      ofNullable(filter.getDecisionKey()).ifPresent(builder::decisionKeys);
      ofNullable(filter.getDmnDecisionId()).ifPresent(builder::dmnDecisionIds);
      ofNullable(filter.getDmnDecisionName()).ifPresent(builder::dmnDecisionNames);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDmnDecisionRequirementsId())
          .ifPresent(builder::dmnDecisionRequirementsIds);
      ofNullable(filter.getDecisionRequirementsKey()).ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }

    return builder.build();
  }

  private static DecisionRequirementsFilter toDecisionRequirementsFilter(
      final DecisionRequirementsFilterRequest filter) {
    final var builder = FilterBuilders.decisionRequirements();

    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getDecisionRequirementsKey())
                  .ifPresent(builder::decisionRequirementsKeys);
              Optional.ofNullable(f.getDmnDecisionRequirementsName())
                  .ifPresent(builder::dmnDecisionRequirementsNames);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getDmnDecisionRequirementsId())
                  .ifPresent(builder::dmnDecisionRequirementsIds);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
            });

    return builder.build();
  }

  private static UserTaskFilter toUserTaskFilter(final UserTaskFilterRequest filter) {
    final var builder = FilterBuilders.userTask();

    if (filter != null) {
      // key
      if (filter.getKey() != null) {
        builder.keys(filter.getKey());
      }

      // state
      if (filter.getState() != null && !filter.getState().isEmpty()) {
        builder.states(filter.getState());
      }

      // bpmnProcessId
      if (filter.getBpmnDefinitionId() != null && !filter.getBpmnDefinitionId().isEmpty()) {
        builder.bpmnProcessIds(filter.getBpmnDefinitionId());
      }

      // elementId
      if (filter.getElementId() != null && !filter.getElementId().isEmpty()) {
        builder.elementIds(filter.getElementId());
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
      if (filter.getProcessDefinitionKey() != null) {
        builder.processDefinitionKeys(filter.getProcessDefinitionKey());
      }

      // processInstanceKey
      if (filter.getProcessInstanceKey() != null) {
        builder.processInstanceKeys(filter.getProcessInstanceKey());
      }

      // tenantIds
      if (filter.getTenantIds() != null) {
        builder.tenantIds(filter.getTenantIds());
      }
    }

    return builder.build();
  }

  private static IncidentFilter toIncidentFilter(final IncidentFilterRequest filter) {
    final var builder = FilterBuilders.incident();

    /*Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getKey()).ifPresent(builder::key);
            });
    */
    return builder.build();
  }

  private static List<String> applyProcessInstanceSortField(
      final String field, final ProcessInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processInstanceKey" -> builder.processInstanceKey();
        case "startDate" -> builder.startDate();
        case "endDate" -> builder.endDate();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyDecisionDefinitionSortField(
      final String field, final DecisionDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "decisionKey" -> builder.decisionKey();
        case "dmnDecisionId" -> builder.dmnDecisionId();
        case "dmnDecisionName" -> builder.dmnDecisionName();
        case "version" -> builder.version();
        case "dmnDecisionRequirementsId" -> builder.dmnDecisionRequirementsId();
        case "decisionRequirementsKey" -> builder.decisionRequirementsKey();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyDecisionRequirementsSortField(
      final String field, final DecisionRequirementsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "decisionRequirementsKey" -> builder.decisionRequirementsKey();
        case "dmnDecisionRequirementsName" -> builder.dmnDecisionRequirementsName();
        case "version" -> builder.version();
        case "dmnDecisionRequirementsId" -> builder.dmnDecisionRequirementsId();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyUserTaskSortField(
      final String field, final UserTaskSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "creationDate" -> builder.creationDate();
        case "completionDate" -> builder.completionDate();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyIncidentSortField(
      final String field, final IncidentSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } /* else {
        switch (field) {
          case "decisionRequirementsKey" -> builder.decisionRequirementsKey();
          case "dmnDecisionRequirementsName" -> builder.dmnDecisionRequirementsName();
          case "version" -> builder.version();
          case "dmnDecisionRequirementsId" -> builder.dmnDecisionRequirementsId();
          case "tenantId" -> builder.tenantId();
          default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
        }
      }*/
    return validationErrors;
  }

  private static List<VariableValueFilter> toVariableValueFilter(
      final List<VariableValueFilterRequest> filters) {
    if (filters != null && !filters.isEmpty()) {
      return filters.stream().map(SearchQueryRequestMapper::toVariableValueFilter).toList();
    }
    return null;
  }

  private static VariableValueFilter toVariableValueFilter(final VariableValueFilterRequest f) {
    return FilterBuilders.variableValue(
        (v) ->
            v.name(f.getName())
                .eq(f.getEq())
                .gt(f.getGt())
                .gte(f.getGte())
                .lt(f.getLt())
                .lte(f.getLte()));
  }

  private static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final SearchQueryPageRequest requestedPage) {
    if (requestedPage == null) {
      return Either.right(null);
    }

    final Object[] searchAfter = toArrayOrNull(requestedPage.getSearchAfter());
    final Object[] searchBefore = toArrayOrNull(requestedPage.getSearchBefore());

    if (searchAfter != null && searchBefore != null) {
      return Either.left(List.of(ERROR_SEARCH_BEFORE_AND_AFTER));
    }

    return Either.right(
        SearchQueryPage.of(
            (p) ->
                p.size(requestedPage.getLimit())
                    .from(requestedPage.getFrom())
                    .searchAfter(searchAfter)
                    .searchBefore(searchBefore)));
  }

  private static <T, B extends SortOption.AbstractBuilder<B> & ObjectBuilder<T>>
      Either<List<String>, T> toSearchQuerySort(
          final List<SearchQuerySortRequest> sorting,
          final Supplier<B> builderSupplier,
          final BiFunction<String, B, List<String>> sortFieldMapper) {
    if (sorting != null && !sorting.isEmpty()) {
      final List<String> validationErrors = new ArrayList<>();
      final var builder = builderSupplier.get();
      for (final SearchQuerySortRequest sort : sorting) {
        validationErrors.addAll(sortFieldMapper.apply(sort.getField(), builder));
        validationErrors.addAll(applySortOrder(sort.getOrder(), builder));
      }

      return validationErrors.isEmpty()
          ? Either.right(builder.build())
          : Either.left(validationErrors);
    }

    return Either.right(null);
  }

  private static <
          T,
          B extends TypedSearchQueryBuilder<T, B, F, S>,
          F extends FilterBase,
          S extends SortOption>
      Either<ProblemDetail, T> buildSearchQuery(
          final F filter,
          final Either<List<String>, S> sorting,
          final Either<List<String>, SearchQueryPage> page,
          final Supplier<B> queryBuilderSupplier) {
    final List<String> validationErrors = new ArrayList<>();
    if (sorting.isLeft()) {
      validationErrors.addAll(sorting.getLeft());
    }
    if (page.isLeft()) {
      validationErrors.addAll(page.getLeft());
    }

    return RequestMapper.getResult(
        RequestValidator.createProblemDetail(validationErrors),
        () ->
            queryBuilderSupplier.get().page(page.get()).filter(filter).sort(sorting.get()).build());
  }

  private static List<String> applySortOrder(
      final String order, final SortOption.AbstractBuilder<?> builder) {
    final List<String> validationErrors = new ArrayList<>();
    switch (order.toLowerCase()) {
      case "asc" -> builder.asc();
      case "desc" -> builder.desc();
      default -> validationErrors.add(ERROR_UNKNOWN_SORT_ORDER.formatted(order));
    }
    return validationErrors;
  }

  private static Object[] toArrayOrNull(final List<Object> values) {
    if (values == null || values.isEmpty()) {
      return null;
    } else {
      return values.toArray();
    }
  }
}
