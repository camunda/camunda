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

import io.camunda.service.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.service.entities.DecisionInstanceEntity.DecisionInstanceType;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.entities.IncidentEntity.IncidentState;
import io.camunda.service.search.filter.DateValueFilter;
import io.camunda.service.search.filter.DecisionDefinitionFilter;
import io.camunda.service.search.filter.DecisionInstanceFilter;
import io.camunda.service.search.filter.DecisionRequirementsFilter;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.FlowNodeInstanceFilter;
import io.camunda.service.search.filter.IncidentFilter;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.filter.ProcessInstanceVariableFilter;
import io.camunda.service.search.filter.UserFilter;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.FlowNodeInstanceQuery;
import io.camunda.service.search.query.IncidentQuery;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.TypedSearchQueryBuilder;
import io.camunda.service.search.query.UserQuery;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.sort.DecisionDefinitionSort;
import io.camunda.service.search.sort.DecisionInstanceSort;
import io.camunda.service.search.sort.DecisionRequirementsSort;
import io.camunda.service.search.sort.FlowNodeInstanceSort;
import io.camunda.service.search.sort.IncidentSort;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.search.sort.SortOption;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.service.search.sort.UserSort;
import io.camunda.service.search.sort.UserTaskSort;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceVariableFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskVariableFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableValueFilterRequest;
import io.camunda.zeebe.gateway.rest.validator.RequestValidator;
import io.camunda.zeebe.util.Either;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
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

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toFlownodeInstanceQuery(
      final FlowNodeInstanceSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::flownodeInstance,
            SearchQueryRequestMapper::applyFlownodeInstanceSortField);
    final var filter = toFlownodeInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQuery(
      final DecisionInstanceSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::decisionInstance,
            SearchQueryRequestMapper::applyDecisionInstanceSortField);
    final var filter = toDecisionInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  private static DecisionInstanceFilter toDecisionInstanceFilter(
      final DecisionInstanceFilterRequest filter) {
    final var builder = FilterBuilders.decisionInstance();

    if (filter != null) {
      ofNullable(filter.getKey()).ifPresent(builder::keys);
      ofNullable(filter.getState())
          .map(s -> convertEnum(s, DecisionInstanceState.class))
          .ifPresent(builder::states);
      ofNullable(filter.getEvaluationFailure()).ifPresent(builder::evaluationFailures);
      ofNullable(filter.getProcessDefinitionKey()).ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionKey()).ifPresent(builder::decisionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::dmnDecisionIds);
      ofNullable(filter.getDecisionDefinitionName()).ifPresent(builder::dmnDecisionNames);
      ofNullable(filter.getDecisionDefinitionVersion()).ifPresent(builder::decisionVersions);
      ofNullable(filter.getDecisionDefinitionType())
          .map(t -> convertEnum(t, DecisionInstanceType.class))
          .ifPresent(builder::decisionTypes);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }

  private static List<String> applyDecisionInstanceSortField(
      final String field, final DecisionInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "key" -> builder.key();
        case "state" -> builder.state();
        case "evaluationDate" -> builder.evaluationDate();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "decisionKey" -> builder.decisionKey();
        case "dmnDecisionId" -> builder.dmnDecisionId();
        case "dmnDecisionName" -> builder.dmnDecisionName();
        case "decisionVersion" -> builder.decisionVersion();
        case "decisionType" -> builder.decisionType();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static <Q extends Enum<Q>, E extends Enum<E>> E convertEnum(
      @NotNull final Q sourceEnum, @NotNull final Class<E> targetEnumType) {
    return Enum.valueOf(targetEnumType, sourceEnum.name());
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

  public static Either<ProblemDetail, UserQuery> toUserQuery(final UserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::user,
            SearchQueryRequestMapper::applyUserSortField);
    final var filter = toUserFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userSearchQuery);
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
      ofNullable(filter.getRunning())
          .ifPresentOrElse(builder::running, () -> builder.running(false));
      ofNullable(filter.getActive()).ifPresentOrElse(builder::active, () -> builder.active(false));
      ofNullable(filter.getIncidents())
          .ifPresentOrElse(builder::incidents, () -> builder.incidents(false));
      ofNullable(filter.getFinished())
          .ifPresentOrElse(builder::finished, () -> builder.finished(false));
      ofNullable(filter.getCompleted())
          .ifPresentOrElse(builder::completed, () -> builder.completed(false));
      ofNullable(filter.getCanceled())
          .ifPresentOrElse(builder::canceled, () -> builder.canceled(false));
      ofNullable(filter.getRetriesLeft())
          .ifPresentOrElse(builder::retriesLeft, () -> builder.retriesLeft(false));
      ofNullable(filter.getErrorMessage()).ifPresent(builder::errorMessage);
      ofNullable(filter.getActivityId()).ifPresent(builder::activityId);
      ofNullable(toDateValueFilter(filter.getStartDate())).ifPresent(builder::startDate);
      ofNullable(toDateValueFilter(filter.getEndDate())).ifPresent(builder::endDate);
      ofNullable(filter.getBpmnProcessId()).ifPresent(builder::bpmnProcessIds);
      ofNullable(filter.getProcessDefinitionVersion())
          .ifPresent(builder::processDefinitionVersions);
      ofNullable(toProcessInstanceVariableFilter(filter.getVariable()))
          .ifPresent(builder::variable);
      ofNullable(filter.getBatchOperationId()).ifPresent(builder::batchOperationIds);
      ofNullable(filter.getParentProcessInstanceKey())
          .ifPresent(builder::parentProcessInstanceKeys);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }

    return builder.build();
  }

  private static ProcessInstanceVariableFilter toProcessInstanceVariableFilter(
      final ProcessInstanceVariableFilterRequest filter) {
    if (filter != null && filter.getName() != null) {
      final var builder = FilterBuilders.processInstanceVariable();
      return builder.name(filter.getName()).values(filter.getValues()).build();
    }
    return null;
  }

  private static VariableValueFilter toUserTaskVariableFilter(
      final ProcessInstanceVariableFilterRequest filter) {
    if (filter != null && filter.getName() != null) {
      final var builder = FilterBuilders.variableValue();
      return builder.name(filter.getName()).eq(filter.getValues()).build();
    }
    return null;
  }

  private static DecisionDefinitionFilter toDecisionDefinitionFilter(
      final DecisionDefinitionFilterRequest filter) {
    final var builder = FilterBuilders.decisionDefinition();

    if (filter != null) {
      ofNullable(filter.getDecisionDefinitionKey()).ifPresent(builder::decisionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::dmnDecisionIds);
      ofNullable(filter.getDecisionDefinitionName()).ifPresent(builder::dmnDecisionNames);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::dmnDecisionRequirementsIds);
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
              Optional.ofNullable(f.getDecisionRequirementsName())
                  .ifPresent(builder::dmnDecisionRequirementsNames);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getDecisionRequirementsId())
                  .ifPresent(builder::dmnDecisionRequirementsIds);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
            });

    return builder.build();
  }

  private static FlowNodeInstanceFilter toFlownodeInstanceFilter(
      final FlowNodeInstanceFilterRequest filter) {
    final var builder = FilterBuilders.flownodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getFlowNodeInstanceKey())
                  .ifPresent(builder::flowNodeInstanceKeys);
              Optional.ofNullable(f.getProcessInstanceKey())
                  .ifPresent(builder::processInstanceKeys);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getState()).ifPresent(builder::states);
              Optional.ofNullable(f.getType()).ifPresent(builder::types);
              Optional.ofNullable(f.getFlowNodeId()).ifPresent(builder::flowNodeIds);
              Optional.ofNullable(f.getFlowNodeName()).ifPresent(builder::flowNodeNames);
              Optional.ofNullable(f.getTreePath()).ifPresent(builder::treePaths);
              Optional.ofNullable(f.getIncident()).ifPresent(builder::incident);
              Optional.ofNullable(f.getIncidentKey()).ifPresent(builder::incidentKeys);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
            });
    return builder.build();
  }

  private static UserTaskFilter toUserTaskFilter(final UserTaskFilterRequest filter) {
    final var builder = FilterBuilders.userTask();

    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getKey()).ifPresent(builder::keys);
              Optional.ofNullable(f.getState()).ifPresent(builder::states);
              Optional.ofNullable(f.getProcessDefinitionId()).ifPresent(builder::bpmnProcessIds);
              Optional.ofNullable(f.getElementId()).ifPresent(builder::elementIds);
              Optional.ofNullable(f.getAssignee()).ifPresent(builder::assignees);
              Optional.ofNullable(f.getCandidateGroup()).ifPresent(builder::candidateGroups);
              Optional.ofNullable(f.getCandidateUser()).ifPresent(builder::candidateUsers);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getProcessInstanceKey())
                  .ifPresent(builder::processInstanceKeys);
              Optional.ofNullable(f.getTenantIds()).ifPresent(builder::tenantIds);

              Optional.ofNullable(f.getVariables())
                  .filter(variables -> !variables.isEmpty())
                  .ifPresent(vars -> builder.variable(toVariableValueFilters(vars)));
            });

    return builder.build();
  }

  private static UserFilter toUserFilter(final UserFilterRequest filter) {
    return Optional.ofNullable(filter)
        .map(
            f ->
                FilterBuilders.user()
                    .username(f.getUsername())
                    .name(f.getName())
                    .email(f.getEmail())
                    .build())
        .orElse(null);
  }

  private static IncidentFilter toIncidentFilter(final IncidentFilterRequest filter) {
    final var builder = FilterBuilders.incident();

    if (filter != null) {
      ofNullable(filter.getKey()).ifPresent(builder::keys);
      ofNullable(filter.getProcessDefinitionKey()).ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::bpmnProcessIds);
      ofNullable(filter.getProcessInstanceKey()).ifPresent(builder::processInstanceKeys);
      ofNullable(filter.getErrorType())
          .ifPresent(t -> builder.errorTypes(IncidentEntity.ErrorType.valueOf(t.getValue())));
      ofNullable(filter.getErrorMessage()).ifPresent(builder::errorMessages);
      ofNullable(filter.getFlowNodeId()).ifPresent(builder::flowNodeIds);
      ofNullable(filter.getFlowNodeInstanceKey()).ifPresent(builder::flowNodeInstanceKeys);
      ofNullable(filter.getCreationTime())
          .ifPresent(t -> builder.creationTime(toDateValueFilter(t)));
      ofNullable(filter.getState())
          .ifPresent(s -> builder.states(IncidentState.valueOf(s.getValue())));
      ofNullable(filter.getJobKey()).ifPresent(builder::jobKeys);
      ofNullable(filter.getTreePath()).ifPresent(builder::treePaths);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }

  private static List<String> applyProcessInstanceSortField(
      final String field, final ProcessInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "key" -> builder.key();
        case "processName" -> builder.processName();
        case "processVersion" -> builder.processVersion();
        case "bpmnProcessId" -> builder.bpmnProcessId();
        case "parentKey" -> builder.parentKey();
        case "parentFlowNodeInstanceKey" -> builder.parentFlowNodeInstanceKey();
        case "startDate" -> builder.startDate();
        case "endDate" -> builder.endDate();
        case "state" -> builder.state();
        case "incident" -> builder.incident();
        case "hasActiveOperation" -> builder.hasActiveOperation();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "tenantId" -> builder.tenantId();
        case "rootInstanceId" -> builder.rootInstanceId();
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

  private static List<String> applyFlownodeInstanceSortField(
      final String field, final FlowNodeInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    return validationErrors;
  }

  private static List<String> applyIncidentSortField(
      final String field, final IncidentSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "key" -> builder.key();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "bpmnProcessId" -> builder.bpmnProcessId();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "errorType" -> builder.errorType();
        case "errorMessage" -> builder.errorMessage();
        case "flowNodeId" -> builder.flowNodeId();
        case "flowNodeInstanceKey" -> builder.flowNodeInstanceKey();
        case "creationTime" -> builder.creationTime();
        case "state" -> builder.state();
        case "jobKey" -> builder.jobKey();
        case "treePath" -> builder.treePath();
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
        case "priority" -> builder.priority();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyUserSortField(
      final String field, final UserSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "username" -> builder.username();
        case "name" -> builder.name();
        case "email" -> builder.email();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<VariableValueFilter> toVariableValueFilters(
      final List<UserTaskVariableFilterRequest> filters) {

    if (filters != null && !filters.isEmpty()) {
      return filters.stream()
          .map(
              filter ->
                  new VariableValueFilter.Builder()
                      .name(filter.getName())
                      .eq(filter.getValue())
                      .build())
          .toList();
    }

    return null;
  }

  private static VariableValueFilter toVariableValueFilter(
      final VariableValueFilterRequest filter) {
    return Optional.ofNullable(filter)
        .map(
            f ->
                FilterBuilders.variableValue()
                    .name(f.getName())
                    .eq(f.getEq())
                    .neq(f.getNeq())
                    .gt(f.getGt())
                    .gte(f.getGte())
                    .lt(f.getLt())
                    .lte(f.getLte())
                    .build())
        .orElse(null);
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

  private static DateValueFilter toDateValueFilter(final String text) {
    if (StringUtils.isEmpty(text)) {
      return null;
    }
    final var date = OffsetDateTime.parse(text);
    return new DateValueFilter.Builder().before(date).after(date).build();
  }

  private static DateValueFilter toDateValueFilter(final String after, final String before) {
    final Optional<OffsetDateTime> beforeDateTime = ofNullable(before).map(OffsetDateTime::parse);
    final Optional<OffsetDateTime> afterDateTime =
        Optional.ofNullable(after).map(OffsetDateTime::parse);
    if (beforeDateTime.isEmpty() && afterDateTime.isEmpty()) {
      return null;
    }
    return new DateValueFilter.Builder()
        .before(beforeDateTime.orElse(null))
        .after(afterDateTime.orElse(null))
        .build();
  }
}
