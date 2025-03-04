/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.gateway.rest.RequestMapper.getResult;
import static io.camunda.zeebe.gateway.rest.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_SEARCH_BEFORE_AND_AFTER;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_SORT_FIELD_MUST_NOT_BE_NULL;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_UNKNOWN_SORT_BY;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateDate;
import static java.util.Optional.ofNullable;

import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import io.camunda.search.filter.*;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.TypedSearchQueryBuilder;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.GroupSort;
import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.MappingSort;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.RoleSort;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.TenantSort;
import io.camunda.search.sort.UserSort;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.search.sort.VariableSort;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.protocol.rest.*;
import io.camunda.zeebe.gateway.rest.util.KeyUtil;
import io.camunda.zeebe.gateway.rest.util.ProcessInstanceStateConverter;
import io.camunda.zeebe.gateway.rest.validator.RequestValidator;
import io.camunda.zeebe.util.Either;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ProblemDetail;

public final class SearchQueryRequestMapper {

  private SearchQueryRequestMapper() {}

  public static Either<ProblemDetail, UsageMetricsQuery> toUsageMetricsQuery(
      final String startTime, final String endTime) {
    return getResult(
        validate(
            violations -> {
              if (startTime == null || endTime == null) {
                violations.add("The startTime and endTime must both be specified");
              }
              validateDate(startTime, "startTime", violations);
              validateDate(endTime, "endTime", violations);
            }),
        () ->
            new UsageMetricsQuery.Builder()
                .filter(
                    new UsageMetricsFilter.Builder()
                        .startTime(toOffsetDateTime(startTime))
                        .endTime(toOffsetDateTime(endTime))
                        .build())
                .build());
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final ProcessDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessDefinitionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::processDefinition,
            SearchQueryRequestMapper::applyProcessDefinitionSortField);
    final var filter = toProcessDefinitionFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionStatisticsFilter>
      toProcessDefinitionStatisticsQuery(
          final long processDefinitionKey, final ProcessDefinitionFlowNodeStatisticsQuery request) {
    if (request == null) {
      return Either.right(
          new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey).build());
    }
    final var filter = toBaseProcessInstanceFilter(processDefinitionKey, request.getFilter());
    return Either.right(filter);
  }

  private static ProcessDefinitionStatisticsFilter toBaseProcessInstanceFilter(
      final long processDefinitionKey, final BaseProcessInstanceFilter filter) {
    final var builder = FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey);

    if (filter != null) {
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.getParentFlowNodeInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.getStartDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.getEndDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.getVariables())
          .filter(variables -> !variables.isEmpty())
          .ifPresent(vars -> builder.variables(toVariableValueFiltersForProcessInstance(vars)));
    }

    return builder.build();
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final ProcessInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::processInstance,
            SearchQueryRequestMapper::applyProcessInstanceSortField);
    final var filter = toProcessInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(final RoleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::role,
            SearchQueryRequestMapper::applyRoleSortField);
    return buildSearchQuery(null, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQuery(
      final GroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::group,
            SearchQueryRequestMapper::applyGroupSortField);
    return buildSearchQuery(null, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQuery(
      final TenantSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenant,
            SearchQueryRequestMapper::applyTenantSortField);
    final var filter = toTenantFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, MappingQuery> toMappingQuery(
      final MappingSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.mappingSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMappingSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::mapping,
            SearchQueryRequestMapper::applyMappingSortField);
    final var filter = toMappingFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::mappingSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQuery(
      final DecisionDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionDefinitionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionDefinition,
            SearchQueryRequestMapper::applyDecisionDefinitionSortField);
    final var filter = toDecisionDefinitionFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQuery(
      final DecisionRequirementsSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionRequirementsSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionRequirements,
            SearchQueryRequestMapper::applyDecisionRequirementsSortField);
    final var filter = toDecisionRequirementsFilter(request.getFilter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionRequirementsSearchQuery);
  }

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toFlownodeInstanceQuery(
      final FlowNodeInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromFlowNodeInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::flowNodeInstance,
            SearchQueryRequestMapper::applyFlownodeInstanceSortField);
    final var filter = toFlownodeInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQuery(
      final DecisionInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionInstance,
            SearchQueryRequestMapper::applyDecisionInstanceSortField);
    final var filter = toDecisionInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  private static DecisionInstanceFilter toDecisionInstanceFilter(
      final io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceFilter filter) {
    final var builder = FilterBuilders.decisionInstance();

    if (filter != null) {
      ofNullable(filter.getDecisionInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionInstanceKeys);
      ofNullable(filter.getDecisionInstanceId()).ifPresent(builder::decisionInstanceIds);
      ofNullable(filter.getState())
          .map(s -> convertEnum(s, DecisionInstanceState.class))
          .ifPresent(builder::states);
      ofNullable(filter.getEvaluationFailure()).ifPresent(builder::evaluationFailures);
      ofNullable(filter.getEvaluationDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::evaluationDateOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::decisionDefinitionKeyOperations);
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
      final DecisionInstanceSearchQuerySortRequest.FieldEnum field,
      final DecisionInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case DECISION_INSTANCE_KEY -> builder.decisionInstanceKey();
        case DECISION_INSTANCE_ID -> builder.decisionInstanceId();
        case STATE -> builder.state();
        case EVALUATION_DATE -> builder.evaluationDate();
        case EVALUATION_FAILURE -> builder.evaluationFailure();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case DECISION_DEFINITION_KEY -> builder.decisionDefinitionKey();
        case DECISION_DEFINITION_ID -> builder.decisionDefinitionId();
        case DECISION_DEFINITION_NAME -> builder.decisionDefinitionName();
        case DECISION_DEFINITION_VERSION -> builder.decisionDefinitionVersion();
        case DECISION_DEFINITION_TYPE -> builder.decisionDefinitionType();
        case TENANT_ID -> builder.tenantId();
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
      final UserTaskSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::userTask,
            SearchQueryRequestMapper::applyUserTaskSortField);
    final var filter = toUserTaskFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQuery(
      final UserTaskVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }

    final var filter = toUserTaskVariableFilter(request.getFilter());
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskVariableSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::variable,
            SearchQueryRequestMapper::applyUserTaskVariableSortField);

    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  private static VariableFilter toUserTaskVariableFilter(
      final VariableUserTaskFilterRequest filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }

    final var builder = FilterBuilders.variable();
    ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);

    return builder.build();
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQuery(
      final VariableSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromVariableSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::variable,
            SearchQueryRequestMapper::applyVariableSortField);
    final VariableFilter filter = toVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  private static VariableFilter toVariableFilter(
      final io.camunda.zeebe.gateway.protocol.rest.VariableFilter filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }

    final var builder = FilterBuilders.variable();

    ofNullable(filter.getProcessInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getScopeKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::scopeKeyOperations);
    ofNullable(filter.getVariableKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::variableKeyOperations);
    ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);
    ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);
    ofNullable(filter.getValue())
        .map(mapToOperations(String.class))
        .ifPresent(builder::valueOperations);

    return builder.build();
  }

  public static Either<ProblemDetail, UserQuery> toUserQuery(final UserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::user,
            SearchQueryRequestMapper::applyUserSortField);
    final var filter = toUserFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final IncidentSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromIncidentSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::incident,
            SearchQueryRequestMapper::applyIncidentSortField);
    final var filter = toIncidentFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  private static ProcessDefinitionFilter toProcessDefinitionFilter(
      final io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionFilter filter) {
    final var builder = FilterBuilders.processDefinition();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .map(KeyUtil::keyToLong)
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

  private static OffsetDateTime toOffsetDateTime(final String text) {
    return StringUtils.isEmpty(text) ? null : OffsetDateTime.parse(text);
  }

  private static ProcessInstanceFilter toProcessInstanceFilter(
      final io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilter filter) {
    final var builder = FilterBuilders.processInstance();

    if (filter != null) {
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionNameOperations);
      ofNullable(filter.getProcessDefinitionVersion())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::processDefinitionVersionOperations);
      ofNullable(filter.getProcessDefinitionVersionTag())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionVersionTagOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.getParentFlowNodeInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentFlowNodeInstanceKeyOperations);
      ofNullable(filter.getStartDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::startDateOperations);
      ofNullable(filter.getEndDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::endDateOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class, new ProcessInstanceStateConverter()))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.getVariables())
          .filter(variables -> !variables.isEmpty())
          .ifPresent(vars -> builder.variables(toVariableValueFiltersForProcessInstance(vars)));
      ofNullable(filter.getBatchOperationId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
    }

    return builder.build();
  }

  private static TenantFilter toTenantFilter(final TenantFilterRequest filter) {
    final var builder = FilterBuilders.tenant();
    if (filter != null) {
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantId);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  private static MappingFilter toMappingFilter(final MappingFilterRequest filter) {
    final var builder = FilterBuilders.mapping();
    if (filter != null) {
      ofNullable(filter.getClaimName()).ifPresent(builder::claimName);
      ofNullable(filter.getClaimValue()).ifPresent(builder::claimValue);
    }
    return builder.build();
  }

  private static DecisionDefinitionFilter toDecisionDefinitionFilter(
      final io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionFilter filter) {
    final var builder = FilterBuilders.decisionDefinition();

    if (filter != null) {
      ofNullable(filter.getDecisionDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getName()).ifPresent(builder::names);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
      ofNullable(filter.getDecisionRequirementsKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }

    return builder.build();
  }

  private static DecisionRequirementsFilter toDecisionRequirementsFilter(
      final io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsFilter filter) {
    final var builder = FilterBuilders.decisionRequirements();

    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getDecisionRequirementsKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::decisionRequirementsKeys);
              Optional.ofNullable(f.getDecisionRequirementsName()).ifPresent(builder::names);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getDecisionRequirementsId())
                  .ifPresent(builder::decisionRequirementsIds);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
            });

    return builder.build();
  }

  private static FlowNodeInstanceFilter toFlownodeInstanceFilter(
      final io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceFilter filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getFlowNodeInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::flowNodeInstanceKeys);
              Optional.ofNullable(f.getProcessInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processInstanceKeys);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getProcessDefinitionId())
                  .ifPresent(builder::processDefinitionIds);
              Optional.ofNullable(f.getState())
                  .ifPresent(s -> builder.states(FlowNodeState.valueOf(s.getValue())));
              Optional.ofNullable(f.getType())
                  .ifPresent(
                      t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t.getValue())));
              Optional.ofNullable(f.getFlowNodeId()).ifPresent(builder::flowNodeIds);
              Optional.ofNullable(f.getHasIncident()).ifPresent(builder::hasIncident);
              Optional.ofNullable(f.getIncidentKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::incidentKeys);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
            });
    return builder.build();
  }

  private static UserTaskFilter toUserTaskFilter(
      final io.camunda.zeebe.gateway.protocol.rest.UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();

    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getUserTaskKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::userTaskKeys);
              Optional.ofNullable(f.getState())
                  .map(s -> String.valueOf(UserTaskState.valueOf(s.getValue())))
                  .ifPresent(builder::states);
              Optional.ofNullable(f.getProcessDefinitionId()).ifPresent(builder::bpmnProcessIds);
              Optional.ofNullable(f.getElementId()).ifPresent(builder::elementIds);
              Optional.ofNullable(f.getAssignee())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::assigneeOperations);
              Optional.ofNullable(f.getPriority())
                  .map(mapToOperations(Integer.class))
                  .ifPresent(builder::priorityOperations);
              Optional.ofNullable(f.getCandidateGroup())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::candidateGroupOperations);
              Optional.ofNullable(f.getCandidateUser())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::candidateUserOperations);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getProcessInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processInstanceKeys);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
              Optional.ofNullable(f.getElementInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::elementInstanceKeys);
              Optional.ofNullable(f.getProcessInstanceVariables())
                  .filter(variables -> !variables.isEmpty())
                  .ifPresent(
                      vars ->
                          builder.processInstanceVariables(
                              toVariableValueFiltersForUserTask(vars)));
              Optional.ofNullable(f.getLocalVariables())
                  .filter(variables -> !variables.isEmpty())
                  .ifPresent(
                      vars -> builder.localVariables(toVariableValueFiltersForUserTask(vars)));
              Optional.ofNullable(f.getCreationDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::creationDateOperations);
              Optional.ofNullable(f.getCompletionDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::completionDateOperations);
              Optional.ofNullable(f.getDueDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::dueDateOperations);
              Optional.ofNullable(f.getFollowUpDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::followUpDateOperations);
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

  private static IncidentFilter toIncidentFilter(
      final io.camunda.zeebe.gateway.protocol.rest.IncidentFilter filter) {
    final var builder = FilterBuilders.incident();

    if (filter != null) {
      ofNullable(filter.getIncidentKey()).map(KeyUtil::keyToLong).ifPresent(builder::incidentKeys);
      ofNullable(filter.getProcessDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::processDefinitionIds);
      ofNullable(filter.getProcessInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processInstanceKeys);
      ofNullable(filter.getErrorType())
          .ifPresent(t -> builder.errorTypes(IncidentEntity.ErrorType.valueOf(t.getValue())));
      ofNullable(filter.getErrorMessage()).ifPresent(builder::errorMessages);
      ofNullable(filter.getFlowNodeId()).ifPresent(builder::flowNodeIds);
      ofNullable(filter.getFlowNodeInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::flowNodeInstanceKeys);
      ofNullable(filter.getCreationTime())
          .ifPresent(t -> builder.creationTime(toDateValueFilter(t)));
      ofNullable(filter.getState())
          .ifPresent(s -> builder.states(IncidentState.valueOf(s.getValue())));
      ofNullable(filter.getJobKey()).map(KeyUtil::keyToLong).ifPresent(builder::jobKeys);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }

  private static List<String> applyProcessInstanceSortField(
      final ProcessInstanceSearchQuerySortRequest.FieldEnum field,
      final ProcessInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_DEFINITION_NAME -> builder.processDefinitionName();
        case PROCESS_DEFINITION_VERSION -> builder.processDefinitionVersion();
        case PROCESS_DEFINITION_VERSION_TAG -> builder.processDefinitionVersionTag();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PARENT_PROCESS_INSTANCE_KEY -> builder.parentProcessInstanceKey();
        case PARENT_FLOW_NODE_INSTANCE_KEY -> builder.parentFlowNodeInstanceKey();
        case START_DATE -> builder.startDate();
        case END_DATE -> builder.endDate();
        case STATE -> builder.state();
        case HAS_INCIDENT -> builder.hasIncident();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyProcessDefinitionSortField(
      final ProcessDefinitionSearchQuerySortRequest.FieldEnum field,
      final ProcessDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case NAME -> builder.name();
        case RESOURCE_NAME -> builder.resourceName();
        case VERSION -> builder.version();
        case VERSION_TAG -> builder.versionTag();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyRoleSortField(
      final RoleSearchQuerySortRequest.FieldEnum field, final RoleSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case ROLE_KEY -> builder.roleKey();
        case NAME -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyGroupSortField(
      final GroupSearchQuerySortRequest.FieldEnum field, final GroupSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case GROUP_KEY -> builder.groupKey();
        case NAME -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyTenantSortField(
      final TenantSearchQuerySortRequest.FieldEnum field, final TenantSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case KEY -> builder.tenantKey();
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyMappingSortField(
      final MappingSearchQuerySortRequest.FieldEnum field, final MappingSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case MAPPING_KEY -> builder.mappingKey();
        case CLAIM_NAME -> builder.claimName();
        case CLAIM_VALUE -> builder.claimValue();
        case NAME -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyDecisionDefinitionSortField(
      final DecisionDefinitionSearchQuerySortRequest.FieldEnum field,
      final DecisionDefinitionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case DECISION_DEFINITION_KEY -> builder.decisionDefinitionKey();
        case DECISION_DEFINITION_ID -> builder.decisionDefinitionId();
        case NAME -> builder.name();
        case VERSION -> builder.version();
        case DECISION_REQUIREMENTS_ID -> builder.decisionRequirementsId();
        case DECISION_REQUIREMENTS_KEY -> builder.decisionRequirementsKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyDecisionRequirementsSortField(
      final DecisionRequirementsSearchQuerySortRequest.FieldEnum field,
      final DecisionRequirementsSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case DECISION_REQUIREMENTS_KEY -> builder.decisionRequirementsKey();
        case DECISION_REQUIREMENTS_NAME -> builder.name();
        case VERSION -> builder.version();
        case DECISION_REQUIREMENTS_ID -> builder.decisionRequirementsId();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyFlownodeInstanceSortField(
      final FlowNodeInstanceSearchQuerySortRequest.FieldEnum field,
      final FlowNodeInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case FLOW_NODE_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case START_DATE -> builder.startDate();
        case END_DATE -> builder.endDate();
        case FLOW_NODE_ID -> builder.flowNodeId();
        case TYPE -> builder.type();
        case STATE -> builder.state();
        case INCIDENT_KEY -> builder.incidentKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyIncidentSortField(
      final IncidentSearchQuerySortRequest.FieldEnum field, final IncidentSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case INCIDENT_KEY -> builder.incidentKey();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case ERROR_TYPE -> builder.errorType();
        case ERROR_MESSAGE -> builder.errorMessage();
        case FLOW_NODE_ID -> builder.flowNodeId();
        case FLOW_NODE_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case CREATION_TIME -> builder.creationTime();
        case STATE -> builder.state();
        case JOB_KEY -> builder.jobKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyUserTaskSortField(
      final UserTaskSearchQuerySortRequest.FieldEnum field, final UserTaskSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case CREATION_DATE -> builder.creationDate();
        case COMPLETION_DATE -> builder.completionDate();
        case FOLLOW_UP_DATE -> builder.followUpDate();
        case DUE_DATE -> builder.dueDate();
        case PRIORITY -> builder.priority();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyVariableSortField(
      final VariableSearchQuerySortRequest.FieldEnum field, final VariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case VALUE -> builder.value();
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        case VARIABLE_KEY -> builder.variableKey();
        case SCOPE_KEY -> builder.scopeKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyUserTaskVariableSortField(
      final UserTaskVariableSearchQuerySortRequest.FieldEnum field,
      final VariableSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case VALUE -> builder.value();
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        case VARIABLE_KEY -> builder.variableKey();
        case SCOPE_KEY -> builder.scopeKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyUserSortField(
      final UserSearchQuerySortRequest.FieldEnum field, final UserSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case USERNAME -> builder.username();
        case NAME -> builder.name();
        case EMAIL -> builder.email();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<VariableValueFilter> toVariableValueFiltersForUserTask(
      final List<UserTaskVariableFilterRequest> filters) {
    if (filters != null && !filters.isEmpty()) {
      final List<VariableValueFilter> variableValueFilters = new ArrayList<>();
      filters.forEach(
          filter ->
              variableValueFilters.addAll(
                  toVariableValueFilters(filter.getName(), filter.getValue())));
      return variableValueFilters;
    }
    return null;
  }

  private static List<VariableValueFilter> toVariableValueFiltersForProcessInstance(
      final List<ProcessInstanceVariableFilterRequest> filters) {
    if (filters != null && !filters.isEmpty()) {
      final List<VariableValueFilter> variableValueFilters = new ArrayList<>();
      filters.forEach(
          filter ->
              variableValueFilters.addAll(
                  toVariableValueFilters(filter.getName(), filter.getValue())));
      return variableValueFilters;
    }
    return null;
  }

  private static List<VariableValueFilter> toVariableValueFilters(
      String name, StringFilterProperty value) {
    Objects.requireNonNull(value);
    final List<VariableValueFilter> variableValueFilters = new ArrayList<>();
    final List<Operation<String>> operations = mapToOperations(String.class).apply(value);
    for (Operation<String> operation : operations) {
      final UntypedOperation untypedOperation = UntypedOperation.of(operation);
      final VariableValueFilter variableValueFilter =
          new VariableValueFilter.Builder().name(name).valueOperation(untypedOperation).build();
      variableValueFilters.add(variableValueFilter);
    }

    return variableValueFilters;
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

  private static <T, B extends SortOption.AbstractBuilder<B> & ObjectBuilder<T>, F>
      Either<List<String>, T> toSearchQuerySort(
          final List<SearchQuerySortRequest<F>> sorting,
          final Supplier<B> builderSupplier,
          final BiFunction<F, B, List<String>> sortFieldMapper) {
    if (sorting != null && !sorting.isEmpty()) {
      final List<String> validationErrors = new ArrayList<>();
      final var builder = builderSupplier.get();
      for (final SearchQuerySortRequest<F> sort : sorting) {
        validationErrors.addAll(sortFieldMapper.apply(sort.field(), builder));
        applySortOrder(sort.order(), builder);
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

    return getResult(
        RequestValidator.createProblemDetail(validationErrors),
        () ->
            queryBuilderSupplier.get().page(page.get()).filter(filter).sort(sorting.get()).build());
  }

  private static void applySortOrder(
      final SortOrderEnum order, final SortOption.AbstractBuilder<?> builder) {
    if (order == SortOrderEnum.DESC) {
      builder.desc();
    } else {
      builder.asc();
    }
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

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQuery(
      final AuthorizationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.authorizationSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromAuthorizationSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::authorization,
            SearchQueryRequestMapper::applyAuthorizationSortField);
    final var filter = toAuthorizationFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::authorizationSearchQuery);
  }

  private static List<String> applyAuthorizationSortField(
      final AuthorizationSearchQuerySortRequest.FieldEnum field,
      final AuthorizationSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case OWNER_ID -> builder.ownerId();
        case OWNER_TYPE -> builder.ownerType();
        case RESOURCE_ID -> builder.resourceId();
        case RESOURCE_TYPE -> builder.resourceType();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static AuthorizationFilter toAuthorizationFilter(
      final io.camunda.zeebe.gateway.protocol.rest.AuthorizationFilter filter) {
    return Optional.ofNullable(filter)
        .map(
            f ->
                FilterBuilders.authorization()
                    .ownerIds(f.getOwnerId())
                    .ownerType(f.getOwnerType() == null ? null : f.getOwnerType().getValue())
                    .resourceIds(f.getResourceIds())
                    .resourceType(
                        f.getResourceType() == null ? null : f.getResourceType().getValue())
                    .build())
        .orElse(null);
  }
}
