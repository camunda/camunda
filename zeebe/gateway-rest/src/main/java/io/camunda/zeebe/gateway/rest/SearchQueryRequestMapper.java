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
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.*;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateDate;
import static java.util.Optional.ofNullable;

import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter.Builder;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
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
import io.camunda.search.sort.BatchOperationItemSort;
import io.camunda.search.sort.BatchOperationSort;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.GroupSort;
import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.JobSort;
import io.camunda.search.sort.MappingRuleSort;
import io.camunda.search.sort.MessageSubscriptionSort;
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
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ProblemDetail;
import org.springframework.util.CollectionUtils;

public final class SearchQueryRequestMapper {

  public static final AdvancedStringFilter EMPTY_ADVANCED_STRING_FILTER =
      new AdvancedStringFilter();
  public static final BasicStringFilter EMPTY_BASIC_STRING_FILTER = new BasicStringFilter();

  private SearchQueryRequestMapper() {}

  public static Either<ProblemDetail, UsageMetricsQuery> toUsageMetricsQuery(
      final String startTime,
      final String endTime,
      final String tenantId,
      final boolean withTenants) {
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
                        .tenantId(tenantId)
                        .withTenants(withTenants)
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
          final long processDefinitionKey, final ProcessDefinitionElementStatisticsQuery request) {
    if (request == null) {
      return Either.right(
          new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey).build());
    }
    final var filter =
        toProcessDefinitionStatisticsFilter(processDefinitionKey, request.getFilter());

    if (filter.isLeft()) {
      final var problem = RequestValidator.createProblemDetail(filter.getLeft());
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    return Either.right(filter.get());
  }

  public static Either<List<String>, ProcessDefinitionStatisticsFilter>
      toProcessDefinitionStatisticsFilter(
          final long processDefinitionKey,
          final io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionStatisticsFilter filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, ProcessDefinitionStatisticsFilter.Builder> builder =
        toBaseProcessInstanceFilterFields(processDefinitionKey, filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      if (filter.get$Or() != null && !filter.get$Or().isEmpty()) {
        for (final BaseProcessInstanceFilterFields or : filter.get$Or()) {
          final var orBuilder = toBaseProcessInstanceFilterFields(processDefinitionKey, or);
          if (orBuilder.isLeft()) {
            validationErrors.addAll(orBuilder.getLeft());
          } else {
            builder.get().addOrOperation(orBuilder.get().build());
          }
        }
      }
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  private static Either<List<String>, ProcessDefinitionStatisticsFilter.Builder>
      toBaseProcessInstanceFilterFields(
          final long processDefinitionKey, final BaseProcessInstanceFilterFields filter) {
    final var builder = FilterBuilders.processDefinitionStatisticsFilter(processDefinitionKey);
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getParentProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::parentProcessInstanceKeyOperations);
      ofNullable(filter.getParentElementInstanceKey())
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
      ofNullable(filter.getBatchOperationId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getHasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.getElementInstanceState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.getIncidentErrorHashCode())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::incidentErrorHashCodeOperations);
      if (!CollectionUtils.isEmpty(filter.getVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.variables(either.get());
        }
      }
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
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

  public static Either<ProblemDetail, JobQuery> toJobQuery(final JobSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromJobSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::job,
            SearchQueryRequestMapper::applyJobSortField);
    final var filter = toJobFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::jobSearchQuery);
  }

  private static JobFilter toJobFilter(
      final io.camunda.zeebe.gateway.protocol.rest.JobFilter filter) {
    final var builder = FilterBuilders.job();
    if (filter != null) {
      ofNullable(filter.getJobKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::jobKeyOperations);
      ofNullable(filter.getType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::typeOperations);
      ofNullable(filter.getWorker())
          .map(mapToOperations(String.class))
          .ifPresent(builder::workerOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getKind())
          .map(mapToOperations(String.class))
          .ifPresent(builder::kindOperations);
      ofNullable(filter.getListenerEventType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::listenerEventTypeOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::elementIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::elementInstanceKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
      ofNullable(filter.getDeadline())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::deadlineOperations);
      ofNullable(filter.getDeniedReason())
          .map(mapToOperations(String.class))
          .ifPresent(builder::deniedReasonOperations);
      ofNullable(filter.getIsDenied()).ifPresent(builder::isDenied);
      ofNullable(filter.getEndTime())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::endTimeOperations);
      ofNullable(filter.getErrorCode())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorCodeOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasFailedWithRetriesLeft()).ifPresent(builder::hasFailedWithRetriesLeft);
      ofNullable(filter.getRetries())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::retriesOperations);
    }

    return builder.build();
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
    final var filter = toRoleFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(
      final RoleUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::role,
            SearchQueryRequestMapper::applyRoleUserSortField);
    final var filter = FilterBuilders.role().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(
      final RoleGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleGroupSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::role,
            SearchQueryRequestMapper::applyRoleGroupSortField);
    final var filter = FilterBuilders.role().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(
      final RoleClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleClientSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::role,
            SearchQueryRequestMapper::applyRoleClientSortField);
    final var filter = FilterBuilders.role().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
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
    final var filter = toGroupFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQuery(
      final GroupUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::group,
            SearchQueryRequestMapper::applyGroupUserSortField);
    final var filter = FilterBuilders.group().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQuery(
      final GroupClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupClientSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::group,
            SearchQueryRequestMapper::applyGroupClientSortField);
    final var filter = FilterBuilders.group().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupSearchQuery);
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

  public static Either<ProblemDetail, TenantQuery> toTenantQuery(
      final TenantGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantGroupSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenant,
            SearchQueryRequestMapper::applyTenantGroupSortField);
    final var filter = FilterBuilders.tenant().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQuery(
      final TenantUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenant,
            SearchQueryRequestMapper::applyTenantUserSortField);
    final var filter = FilterBuilders.tenant().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQuery(
      final TenantClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantClientSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenant,
            SearchQueryRequestMapper::applyTenantClientSortField);
    final var filter = FilterBuilders.tenant().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, MappingRuleQuery> toMappingRuleQuery(
      final MappingRuleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.mappingRuleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMappingRuleSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::mappingRule,
            SearchQueryRequestMapper::applyMappingRuleSortField);
    final var filter = toMappingRuleFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::mappingRuleSearchQuery);
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

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toElementInstanceQuery(
      final ElementInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromElementInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::flowNodeInstance,
            SearchQueryRequestMapper::applyElementInstanceSortField);
    final var filter = toElementInstanceFilter(request.getFilter());
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
      ofNullable(filter.getProcessInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processInstanceKeys);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
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
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
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

  private static VariableFilter toUserTaskVariableFilter(final UserTaskVariableFilter filter) {
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

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final ProcessInstanceIncidentSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromIncidentSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::incident,
            SearchQueryRequestMapper::applyIncidentSortField);
    return buildSearchQuery(
        toIncidentFilter(null), sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQuery(
      final BatchOperationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::batchOperation,
            SearchQueryRequestMapper::applyBatchOperationSortField);
    final var filter = toBatchOperationFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationQuery);
  }

  private static BatchOperationFilter toBatchOperationFilter(
      final io.camunda.zeebe.gateway.protocol.rest.BatchOperationFilter filter) {
    final var builder = FilterBuilders.batchOperation();

    if (filter != null) {
      ofNullable(filter.getBatchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getOperationType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
    }

    return builder.build();
  }

  private static List<String> applyBatchOperationSortField(
      final BatchOperationSearchQuerySortRequest.FieldEnum field,
      final BatchOperationSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case STATE -> builder.state();
        case OPERATION_TYPE -> builder.operationType();
        case START_DATE -> builder.startDate();
        case END_DATE -> builder.endDate();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQuery(
      final BatchOperationItemSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationItemQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationItemSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::batchOperationItem,
            SearchQueryRequestMapper::applyBatchOperationItemSortField);
    final var filter = toBatchOperationItemFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationItemQuery);
  }

  private static io.camunda.search.filter.BatchOperationItemFilter toBatchOperationItemFilter(
      final io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemFilter filter) {
    final var builder = FilterBuilders.batchOperationItem();

    if (filter != null) {
      ofNullable(filter.getBatchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getItemKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::itemKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
    }

    return builder.build();
  }

  private static List<String> applyBatchOperationItemSortField(
      final BatchOperationItemSearchQuerySortRequest.FieldEnum field,
      final BatchOperationItemSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case STATE -> builder.state();
        case BATCH_OPERATION_KEY -> builder.batchOperationKey();
        case ITEM_KEY -> builder.itemKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static ProcessDefinitionFilter toProcessDefinitionFilter(
      final io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionFilter filter) {
    final var builder = FilterBuilders.processDefinition();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
              Optional.ofNullable(f.getProcessDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              Optional.ofNullable(f.getName())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::nameOperations);
              Optional.ofNullable(f.getResourceName()).ifPresent(builder::resourceNames);
              Optional.ofNullable(f.getVersion()).ifPresent(builder::versions);
              Optional.ofNullable(f.getVersionTag()).ifPresent(builder::versionTags);
              Optional.ofNullable(f.getProcessDefinitionId())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::processDefinitionIdOperations);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
              Optional.ofNullable(f.getHasStartForm()).ifPresent(builder::hasStartForm);
            });
    return builder.build();
  }

  private static OffsetDateTime toOffsetDateTime(final String text) {
    return StringUtils.isEmpty(text) ? null : OffsetDateTime.parse(text);
  }

  public static Either<List<String>, ProcessInstanceFilter> toProcessInstanceFilter(
      final io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilter filter) {
    final List<String> validationErrors = new ArrayList<>();

    final Either<List<String>, Builder> builder = toProcessInstanceFilterFields(filter);
    if (builder.isLeft()) {
      validationErrors.addAll(builder.getLeft());
    }

    if (filter != null) {
      if (filter.get$Or() != null && !filter.get$Or().isEmpty()) {
        for (final ProcessInstanceFilterFields or : filter.get$Or()) {
          final var orBuilder = toProcessInstanceFilterFields(or);
          if (orBuilder.isLeft()) {
            validationErrors.addAll(orBuilder.getLeft());
          } else {
            builder.get().addOrOperation(orBuilder.get().build());
          }
        }
      }
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.get().build())
        : Either.left(validationErrors);
  }

  public static Either<List<String>, ProcessInstanceFilter.Builder> toProcessInstanceFilterFields(
      final io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilterFields filter) {
    final var builder = FilterBuilders.processInstance();
    final List<String> validationErrors = new ArrayList<>();
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
      ofNullable(filter.getParentElementInstanceKey())
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
      ofNullable(filter.getBatchOperationId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationIdOperations);
      ofNullable(filter.getErrorMessage())
          .map(mapToOperations(String.class))
          .ifPresent(builder::errorMessageOperations);
      ofNullable(filter.getHasRetriesLeft()).ifPresent(builder::hasRetriesLeft);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getHasElementInstanceIncident())
          .ifPresent(builder::hasFlowNodeInstanceIncident);
      ofNullable(filter.getElementInstanceState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeInstanceStateOperations);
      ofNullable(filter.getIncidentErrorHashCode())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::incidentErrorHashCodeOperations);
      if (!CollectionUtils.isEmpty(filter.getVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.variables(either.get());
        }
      }
    }
    return validationErrors.isEmpty() ? Either.right(builder) : Either.left(validationErrors);
  }

  private static TenantFilter toTenantFilter(
      final io.camunda.zeebe.gateway.protocol.rest.TenantFilter filter) {
    final var builder = FilterBuilders.tenant();
    if (filter != null) {
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantId);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  private static GroupFilter toGroupFilter(
      final io.camunda.zeebe.gateway.protocol.rest.GroupFilter filter) {
    final var builder = FilterBuilders.group();
    if (filter != null) {
      ofNullable(filter.getGroupId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::groupIdOperations);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  private static RoleFilter toRoleFilter(
      final io.camunda.zeebe.gateway.protocol.rest.RoleFilter filter) {
    final var builder = FilterBuilders.role();
    if (filter != null) {
      ofNullable(filter.getRoleId()).ifPresent(builder::roleId);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }

  private static MappingRuleFilter toMappingRuleFilter(
      final io.camunda.zeebe.gateway.protocol.rest.MappingRuleFilter filter) {
    final var builder = FilterBuilders.mappingRule();
    if (filter != null) {
      ofNullable(filter.getClaimName()).ifPresent(builder::claimName);
      ofNullable(filter.getClaimValue()).ifPresent(builder::claimValue);
      ofNullable(filter.getName()).ifPresent(builder::name);
      ofNullable(filter.getMappingRuleId()).ifPresent(builder::mappingRuleId);
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
              Optional.ofNullable(f.getResourceName()).ifPresent(builder::resourceNames);
            });

    return builder.build();
  }

  private static FlowNodeInstanceFilter toElementInstanceFilter(
      final io.camunda.zeebe.gateway.protocol.rest.ElementInstanceFilter filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              Optional.ofNullable(f.getElementInstanceKey())
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
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::stateOperations);
              Optional.ofNullable(f.getType())
                  .ifPresent(
                      t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t.getValue())));
              Optional.ofNullable(f.getElementId()).ifPresent(builder::flowNodeIds);
              Optional.ofNullable(f.getElementName()).ifPresent(builder::flowNodeNames);
              Optional.ofNullable(f.getHasIncident()).ifPresent(builder::hasIncident);
              Optional.ofNullable(f.getIncidentKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::incidentKeys);
              Optional.ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
              Optional.ofNullable(filter.getStartDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::startDateOperations);
              Optional.ofNullable(filter.getEndDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::endDateOperations);
            });
    return builder.build();
  }

  private static Either<List<String>, UserTaskFilter> toUserTaskFilter(
      final io.camunda.zeebe.gateway.protocol.rest.UserTaskFilter filter) {
    final var builder = FilterBuilders.userTask();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      Optional.ofNullable(filter.getUserTaskKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::userTaskKeys);
      Optional.ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      Optional.ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::bpmnProcessIds);
      Optional.ofNullable(filter.getElementId()).ifPresent(builder::elementIds);
      Optional.ofNullable(filter.getName()).ifPresent(builder::names);
      Optional.ofNullable(filter.getAssignee())
          .map(mapToOperations(String.class))
          .ifPresent(builder::assigneeOperations);
      Optional.ofNullable(filter.getPriority())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      Optional.ofNullable(filter.getCandidateGroup())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateGroupOperations);
      Optional.ofNullable(filter.getCandidateUser())
          .map(mapToOperations(String.class))
          .ifPresent(builder::candidateUserOperations);
      Optional.ofNullable(filter.getProcessDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processDefinitionKeys);
      Optional.ofNullable(filter.getProcessInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::processInstanceKeys);
      Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      Optional.ofNullable(filter.getElementInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::elementInstanceKeys);
      if (!CollectionUtils.isEmpty(filter.getProcessInstanceVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getProcessInstanceVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.processInstanceVariables(either.get());
        }
      }
      if (!CollectionUtils.isEmpty(filter.getLocalVariables())) {
        final Either<List<String>, List<VariableValueFilter>> either =
            toVariableValueFilters(filter.getLocalVariables());
        if (either.isLeft()) {
          validationErrors.addAll(either.getLeft());
        } else {
          builder.localVariables(either.get());
        }
      }
      Optional.ofNullable(filter.getCreationDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::creationDateOperations);
      Optional.ofNullable(filter.getCompletionDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::completionDateOperations);
      Optional.ofNullable(filter.getDueDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::dueDateOperations);
      Optional.ofNullable(filter.getFollowUpDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::followUpDateOperations);
    }

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }

  private static UserFilter toUserFilter(
      final io.camunda.zeebe.gateway.protocol.rest.UserFilter filter) {

    final var builder = FilterBuilders.user();
    if (filter != null) {
      Optional.ofNullable(filter.getUsername())
          .map(mapToOperations(String.class))
          .ifPresent(builder::usernameOperations);
      Optional.ofNullable(filter.getName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      Optional.ofNullable(filter.getEmail())
          .map(mapToOperations(String.class))
          .ifPresent(builder::emailOperations);
    }
    return builder.build();
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
      ofNullable(filter.getErrorType()).ifPresent(t -> builder.errorTypes(t.getValue()));
      ofNullable(filter.getErrorMessage()).ifPresent(builder::errorMessages);
      ofNullable(filter.getElementId()).ifPresent(builder::flowNodeIds);
      ofNullable(filter.getElementInstanceKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::flowNodeInstanceKeys);
      ofNullable(filter.getCreationTime())
          .ifPresent(t -> builder.creationTime(toOffsetDateTime(t)));
      ofNullable(filter.getState()).ifPresent(s -> builder.states(s.getValue()));
      ofNullable(filter.getJobKey()).map(KeyUtil::keyToLong).ifPresent(builder::jobKeys);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }

  private static MessageSubscriptionFilter toMessageSubscriptionFilter(
      final io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionFilter filter) {
    final var builder = FilterBuilders.messageSubscription();

    if (filter != null) {
      ofNullable(filter.getMessageSubscriptionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::messageSubscriptionKeyOperations);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getElementId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(filter.getElementInstanceKey())
          .map(mapToOperations(Long.class))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.getMessageSubscriptionType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageSubscriptionTypeOperations);
      ofNullable(filter.getLastUpdatedDate())
          .map(mapToOperations(OffsetDateTime.class))
          .ifPresent(builder::dateTimeOperations);
      ofNullable(filter.getMessageName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::messageNameOperations);
      ofNullable(filter.getCorrelationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::correlationKeyOperations);
      ofNullable(filter.getTenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
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
        case PARENT_ELEMENT_INSTANCE_KEY -> builder.parentFlowNodeInstanceKey();
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

  private static List<String> applyJobSortField(
      final JobSearchQuerySortRequest.FieldEnum field, final JobSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case ELEMENT_INSTANCE_KEY -> builder.elementInstanceKey();
        case ELEMENT_ID -> builder.elementId();
        case JOB_KEY -> builder.jobKey();
        case TYPE -> builder.type();
        case WORKER -> builder.worker();
        case STATE -> builder.state();
        case KIND -> builder.jobKind();
        case LISTENER_EVENT_TYPE -> builder.listenerEventType();
        case END_TIME -> builder.endTime();
        case TENANT_ID -> builder.tenantId();
        case RETRIES -> builder.retries();
        case IS_DENIED -> builder.isDenied();
        case DENIED_REASON -> builder.deniedReason();
        case HAS_FAILED_WITH_RETRIES_LEFT -> builder.hasFailedWithRetriesLeft();
        case ERROR_CODE -> builder.errorCode();
        case ERROR_MESSAGE -> builder.errorMessage();
        case DEADLINE -> builder.deadline();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
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
        case NAME -> builder.name();
        case ROLE_ID -> builder.roleId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyRoleGroupSortField(
      final RoleGroupSearchQuerySortRequest.FieldEnum field, final RoleSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case GROUP_ID -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyRoleUserSortField(
      final RoleUserSearchQuerySortRequest.FieldEnum field, final RoleSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case USERNAME -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyRoleClientSortField(
      final RoleClientSearchQuerySortRequest.FieldEnum field, final RoleSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case CLIENT_ID -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyGroupSortField(
      final GroupSearchQuerySortRequest.FieldEnum field, final GroupSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case GROUP_ID -> builder.groupId();
        case NAME -> builder.name();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyGroupUserSortField(
      final GroupUserSearchQuerySortRequest.FieldEnum field, final GroupSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case USERNAME -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyGroupClientSortField(
      final GroupClientSearchQuerySortRequest.FieldEnum field, final GroupSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case CLIENT_ID -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyTenantSortField(
      final TenantSearchQuerySortRequest.FieldEnum field, final TenantSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case NAME -> builder.name();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static List<String> applyTenantUserSortField(
      final TenantUserSearchQuerySortRequest.FieldEnum field, final TenantSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case USERNAME -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyTenantGroupSortField(
      final TenantGroupSearchQuerySortRequest.FieldEnum field, final TenantSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case GROUP_ID -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyTenantClientSortField(
      final TenantClientSearchQuerySortRequest.FieldEnum field, final TenantSort.Builder builder) {
    return switch (field) {
      case null -> List.of(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
      case CLIENT_ID -> {
        builder.memberId();
        yield List.of();
      }
    };
  }

  private static List<String> applyMappingRuleSortField(
      final MappingRuleSearchQuerySortRequest.FieldEnum field,
      final MappingRuleSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case MAPPING_RULE_ID -> builder.mappingRuleId();
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

  private static List<String> applyElementInstanceSortField(
      final ElementInstanceSearchQuerySortRequest.FieldEnum field,
      final FlowNodeInstanceSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case START_DATE -> builder.startDate();
        case END_DATE -> builder.endDate();
        case ELEMENT_ID -> builder.flowNodeId();
        case ELEMENT_NAME -> builder.flowNodeName();
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
        case ELEMENT_ID -> builder.flowNodeId();
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
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
        case NAME -> builder.name();
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

  private static List<String> applyMessageSubscriptionSortField(
      final MessageSubscriptionSearchQuerySortRequest.FieldEnum field,
      final MessageSubscriptionSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case MESSAGE_SUBSCRIPTION_KEY -> builder.messageSubscriptionKey();
        case PROCESS_DEFINITION_ID -> builder.processDefinitionId();
        case PROCESS_DEFINITION_KEY -> builder.processDefinitionKey();
        case PROCESS_INSTANCE_KEY -> builder.processInstanceKey();
        case ELEMENT_ID -> builder.flowNodeId();
        case ELEMENT_INSTANCE_KEY -> builder.flowNodeInstanceKey();
        case MESSAGE_SUBSCRIPTION_TYPE -> builder.messageSubscriptionType();
        case LAST_UPDATED_DATE -> builder.dateTime();
        case MESSAGE_NAME -> builder.messageName();
        case CORRELATION_KEY -> builder.correlationKey();
        case TENANT_ID -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static Either<List<String>, List<VariableValueFilter>> toVariableValueFilters(
      final List<VariableValueFilterProperty> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return Either.right(List.of());
    }

    final List<String> validationErrors = new ArrayList<>();
    final List<VariableValueFilter> variableValueFilters =
        filters.stream()
            .flatMap(
                filter -> {
                  if (filter.getName() == null) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_NAME);
                  }
                  if (filter.getValue() == null
                      || filter.getValue().equals(EMPTY_ADVANCED_STRING_FILTER)
                      || filter.getValue().equals(EMPTY_BASIC_STRING_FILTER)) {
                    validationErrors.add(ERROR_MESSAGE_NULL_VARIABLE_VALUE);
                  }
                  // if there is no validation error overall, process the filter
                  return validationErrors.isEmpty()
                      ? toVariableValueFilters(filter.getName(), filter.getValue()).stream()
                      : Stream.empty();
                })
            .toList();
    return validationErrors.isEmpty()
        ? Either.right(variableValueFilters)
        : Either.left(validationErrors);
  }

  private static List<VariableValueFilter> toVariableValueFilters(
      final String name, final StringFilterProperty value) {
    final List<Operation<String>> operations = mapToOperations(String.class).apply(value);
    return new VariableValueFilter.Builder()
        .name(name)
        .valueTypedOperations(operations)
        .buildList();
  }

  private static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final SearchQueryPageRequest requestedPage) {
    if (requestedPage == null) {
      return Either.right(null);
    }

    if (requestedPage.getAfter() != null && requestedPage.getBefore() != null) {
      return Either.left(List.of(ERROR_SEARCH_BEFORE_AND_AFTER));
    }
    if (requestedPage.getFrom() != null
        && (requestedPage.getAfter() != null || requestedPage.getBefore() != null)) {
      return Either.left(List.of(ERROR_SEARCH_BEFORE_AND_AFTER_AND_FROM));
    }

    return Either.right(
        SearchQueryPage.of(
            (p) ->
                p.size(requestedPage.getLimit())
                    .from(requestedPage.getFrom())
                    .after(requestedPage.getAfter())
                    .before(requestedPage.getBefore())));
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
          final Either<List<String>, S> sorting,
          final Either<List<String>, SearchQueryPage> page,
          final Supplier<B> queryBuilderSupplier) {
    return buildSearchQuery(Either.right(null), sorting, page, queryBuilderSupplier);
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
    return buildSearchQuery(Either.right(filter), sorting, page, queryBuilderSupplier);
  }

  private static <
          T,
          B extends TypedSearchQueryBuilder<T, B, F, S>,
          F extends FilterBase,
          S extends SortOption>
      Either<ProblemDetail, T> buildSearchQuery(
          final Either<List<String>, F> filter,
          final Either<List<String>, S> sorting,
          final Either<List<String>, SearchQueryPage> page,
          final Supplier<B> queryBuilderSupplier) {
    final List<String> validationErrors = new ArrayList<>();
    if (filter.isLeft()) {
      validationErrors.addAll(filter.getLeft());
    }
    if (sorting.isLeft()) {
      validationErrors.addAll(sorting.getLeft());
    }
    if (page.isLeft()) {
      validationErrors.addAll(page.getLeft());
    }

    return getResult(
        RequestValidator.createProblemDetail(validationErrors),
        () ->
            queryBuilderSupplier
                .get()
                .page(page.get())
                .filter(filter.get())
                .sort(sorting.get())
                .build());
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

  public static Either<ProblemDetail, TenantQuery> toTenantMemberQuery(
      final String tenantId, final GroupSearchQueryRequest query) {
    return Either.right(
        new TenantQuery.Builder()
            .filter(f -> f.joinParentId(tenantId).memberType(EntityType.GROUP))
            .build());
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQuery(
      final MessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.messageSubscriptionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMessageSubscriptionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::messageSubscription,
            SearchQueryRequestMapper::applyMessageSubscriptionSortField);
    final var filter = toMessageSubscriptionFilter(request.getFilter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::messageSubscriptionSearchQuery);
  }
}
