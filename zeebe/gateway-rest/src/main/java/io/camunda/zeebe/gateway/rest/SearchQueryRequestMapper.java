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

import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.TypedSearchQueryBuilder;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.UserSort;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.search.sort.VariableSort;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.protocol.rest.*;
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

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final ProcessDefinitionSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::processDefinition,
            SearchQueryRequestMapper::applyProcessDefinitionSortField);
    final var filter = toProcessDefinitionFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processDefinitionSearchQuery);
  }

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
            SortOptionBuilders::flowNodeInstance,
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
      ofNullable(filter.getDecisionInstanceKey()).ifPresent(builder::decisionInstanceKeys);
      ofNullable(filter.getState())
          .map(s -> convertEnum(s, DecisionInstanceState.class))
          .ifPresent(builder::states);
      ofNullable(filter.getEvaluationFailure()).ifPresent(builder::evaluationFailures);
      ofNullable(filter.getProcessDefinitionKey()).ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionKey()).ifPresent(builder::decisionDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getDecisionDefinitionName()).ifPresent(builder::decisionDefinitionNames);
      ofNullable(filter.getDecisionDefinitionVersion())
          .ifPresent(builder::decisionDefinitionVersions);
      ofNullable(filter.getDecisionDefinitionType())
          .map(t -> convertEnum(t, DecisionDefinitionType.class))
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
        case "decisionInstanceKey" -> builder.decisionInstanceKey();
        case "state" -> builder.state();
        case "evaluationDate" -> builder.evaluationDate();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "decisionDefinitionKey" -> builder.decisionDefinitionKey();
        case "decisionDefinitionId" -> builder.decisionDefinitionId();
        case "decisionDefinitionName" -> builder.decisionDefinitionName();
        case "decisionDefinitionVersion" -> builder.decisionDefinitionVersion();
        case "decisionDefinitionType" -> builder.decisionDefinitionType();
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

  public static Either<ProblemDetail, VariableQuery> toVariableQuery(
      final VariableSearchQueryRequest request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            request.getSort(),
            SortOptionBuilders::variable,
            SearchQueryRequestMapper::applyVariableSortField);
    final VariableFilter filter = toVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  private static VariableFilter toVariableFilter(final VariableFilterRequest filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }

    final var builder = FilterBuilders.variable();

    ofNullable(filter.getProcessInstanceKey()).ifPresent(builder::processInstanceKeys);
    ofNullable(filter.getScopeKey()).ifPresent(builder::scopeKeys);
    ofNullable(filter.getVariableKey()).ifPresent(builder::variableKeys);
    ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);

    if (filter.getName() != null || filter.getValue() != null) {
      final VariableValueFilter variableValueFilter =
          new VariableValueFilter.Builder().name(filter.getName()).eq(filter.getValue()).build();

      builder.variable(variableValueFilter);
    }

    return builder.build();
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

  private static ProcessDefinitionFilter toProcessDefinitionFilter(
      final ProcessDefinitionFilterRequest filter) {
    final var builder = FilterBuilders.processDefinition();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getName()).ifPresent(builder::names);
              Optional.ofNullable(f.getResourceName()).ifPresent(builder::resourceNames);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getVersionTag()).ifPresent(builder::versionTags);
              Optional.ofNullable(f.getProcessDefinitionId())
                  .ifPresent(builder::processDefinitionIds);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
            });
    return builder.build();
  }

  private static ProcessInstanceFilter toProcessInstanceFilter(
      final ProcessInstanceFilterRequest filter) {
    final var builder = FilterBuilders.processInstance();

    if (filter != null) {
      ofNullable(filter.getProcessInstanceKey()).ifPresent(builder::processInstanceKeys);
      ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::processDefinitionIds);
      ofNullable(filter.getProcessDefinitionName()).ifPresent(builder::processDefinitionNames);
      ofNullable(filter.getProcessDefinitionVersion())
          .ifPresent(builder::processDefinitionVersions);
      ofNullable(filter.getProcessDefinitionVersionTag())
          .ifPresent(builder::processDefinitionVersionTags);
      ofNullable(filter.getProcessDefinitionKey()).ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getRootProcessInstanceKey()).ifPresent(builder::rootProcessInstanceKeys);
      ofNullable(filter.getParentProcessInstanceKey())
          .ifPresent(builder::parentProcessInstanceKeys);
      ofNullable(filter.getParentFlowNodeInstanceKey())
          .ifPresent(builder::parentFlowNodeInstanceKeys);
      ofNullable(filter.getTreePath()).ifPresent(builder::treePaths);
      ofNullable(toDateValueFilter(filter.getStartDate())).ifPresent(builder::startDate);
      ofNullable(toDateValueFilter(filter.getEndDate())).ifPresent(builder::endDate);
      ofNullable(filter.getState()).ifPresent(state -> builder.states(state.getValue()));
      ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }

    return builder.build();
  }

  private static DecisionDefinitionFilter toDecisionDefinitionFilter(
      final DecisionDefinitionFilterRequest filter) {
    final var builder = FilterBuilders.decisionDefinition();

    if (filter != null) {
      ofNullable(filter.getDecisionDefinitionKey()).ifPresent(builder::decisionDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getName()).ifPresent(builder::names);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
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
              Optional.ofNullable(f.getName()).ifPresent(builder::names);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getDecisionRequirementsId())
                  .ifPresent(builder::decisionRequirementsIds);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
            });

    return builder.build();
  }

  private static FlowNodeInstanceFilter toFlownodeInstanceFilter(
      final FlowNodeInstanceFilterRequest filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getFlowNodeInstanceKey())
                  .ifPresent(builder::flowNodeInstanceKeys);
              Optional.ofNullable(f.getProcessInstanceKey())
                  .ifPresent(builder::processInstanceKeys);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getProcessDefinitionId())
                  .ifPresent(builder::processDefinitionIds);
              Optional.ofNullable(f.getState())
                  .ifPresent(s -> builder.states(FlowNodeState.valueOf(s.getValue())));
              Optional.ofNullable(f.getType())
                  .ifPresent(
                      t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t.getValue())));
              Optional.ofNullable(f.getFlowNodeId()).ifPresent(builder::flowNodeIds);
              Optional.ofNullable(f.getTreePath()).ifPresent(builder::treePaths);
              Optional.ofNullable(f.getHasIncident()).ifPresent(builder::hasIncident);
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
              Optional.ofNullable(f.getUserTaskKey()).ifPresent(builder::userTaskKeys);
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
      ofNullable(filter.getIncidentKey()).ifPresent(builder::incidentKeys);
      ofNullable(filter.getProcessDefinitionKey()).ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::processDefinitionIds);
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
        case "key" -> builder.processInstanceKey();
        case "bpmnProcessId" -> builder.processDefinitionId();
        case "processName" -> builder.processDefinitionName();
        case "processVersion" -> builder.processDefinitionVersion();
        case "processVersionTag" -> builder.processDefinitionVersionTag();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "rootProcessInstanceKey" -> builder.rootProcessInstanceKey();
        case "parentProcessInstanceKey" -> builder.parentProcessInstanceKey();
        case "parentFlowNodeInstanceKey" -> builder.parentFlowNodeInstanceKey();
        case "treePath" -> builder.treePath();
        case "startDate" -> builder.startDate();
        case "endDate" -> builder.endDate();
        case "state" -> builder.state();
        case "incident" -> builder.hasIncident();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyProcessDefinitionSortField(
      final String field, final ProcessDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "name" -> builder.name();
        case "resourceName" -> builder.resourceName();
        case "version" -> builder.version();
        case "versionTag" -> builder.versionTag();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "tenantId" -> builder.tenantId();
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
        case "decisionDefinitionKey" -> builder.decisionDefinitionKey();
        case "decisionDefinitionId" -> builder.decisionDefinitionId();
        case "name" -> builder.name();
        case "version" -> builder.version();
        case "decisionRequirementsId" -> builder.decisionRequirementsId();
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
        case "name" -> builder.name();
        case "version" -> builder.version();
        case "decisionRequirementsId" -> builder.decisionRequirementsId();
        case "tenantId" -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyFlownodeInstanceSortField(
      final String field, final FlowNodeInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "flowNodeInstanceKey" -> builder.flowNodeInstanceKey();
        case "processInstanceKey" -> builder.processInstanceKey();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processDefinitionId" -> builder.processDefinitionId();
        case "startDate" -> builder.startDate();
        case "endDate" -> builder.endDate();
        case "flowNodeId" -> builder.flowNodeId();
        case "type" -> builder.type();
        case "state" -> builder.state();
        case "incidentKey" -> builder.incidentKey();
        case "tenantId" -> builder.tenantId();
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
    } else {
      switch (field) {
        case "incidentKey" -> builder.incidentKey();
        case "processDefinitionKey" -> builder.processDefinitionKey();
        case "processDefinitionId" -> builder.processDefinitionId();
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

  private static List<String> applyVariableSortField(
      final String field, final VariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case "value" -> builder.value();
        case "name" -> builder.name();
        case "tenantId" -> builder.tenantId();
        case "variableKey" -> builder.variableKey();
        case "scopeKey" -> builder.scopeKey();
        case "processInstanceKey" -> builder.processInstanceKey();
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
