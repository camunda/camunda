/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.RequestMapper.getResult;
import static io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper.toIncidentFilter;
import static io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper.toProcessDefinitionInstanceVersionStatisticsFilter;
import static io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper.toProcessInstanceFilter;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.*;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;

import io.camunda.gateway.mapping.http.validator.RequestValidator;
import io.camunda.gateway.protocol.model.*;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobErrorStatisticsQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.TypedSearchQueryBuilder;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.zeebe.util.Either;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

public final class SearchQueryRequestMapper {

  public static final AdvancedStringFilter EMPTY_ADVANCED_STRING_FILTER =
      new AdvancedStringFilter();
  public static final BasicStringFilter EMPTY_BASIC_STRING_FILTER = new BasicStringFilter();
  public static final io.camunda.gateway.protocol.model.ProcessInstanceFilter
      EMPTY_PROCESS_INSTANCE_FILTER = new io.camunda.gateway.protocol.model.ProcessInstanceFilter();
  public static final io.camunda.gateway.protocol.model.DecisionInstanceFilter
      EMPTY_DECISION_INSTANCE_FILTER =
          new io.camunda.gateway.protocol.model.DecisionInstanceFilter();

  public static final io.camunda.gateway.protocol.model
          .IncidentProcessInstanceStatisticsByDefinitionFilter
      EMPTY_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_FILTER =
          new io.camunda.gateway.protocol.model
              .IncidentProcessInstanceStatisticsByDefinitionFilter();

  private SearchQueryRequestMapper() {}

  public static Either<ProblemDetail, UsageMetricsQuery> toUsageMetricsQuery(
      final String startTime,
      final String endTime,
      final String tenantId,
      final boolean withTenants) {
    return getResult(
        validate(
            violations -> {
              if (StringUtils.isAnyBlank(startTime, endTime)) {
                violations.add("The startTime and endTime must both be specified");
              }
              final var startDateTime = validateDate(startTime, "startTime", violations);
              final var endDateTime = validateDate(endTime, "endTime", violations);
              if (startDateTime != null
                  && endDateTime != null
                  && !endDateTime.isAfter(startDateTime)) {
                violations.add("The endTime must be after startTime");
              }
            }),
        () ->
            new UsageMetricsQuery.Builder()
                .filter(
                    new UsageMetricsFilter.Builder()
                        .startTime(SearchQueryFilterMapper.toOffsetDateTime(startTime))
                        .endTime(SearchQueryFilterMapper.toOffsetDateTime(endTime))
                        .tenantId(tenantId)
                        .withTenants(withTenants)
                        .build())
                .build());
  }

  public static Either<ProblemDetail, io.camunda.search.query.GlobalJobStatisticsQuery>
      toGlobalJobStatisticsQuery(
          final OffsetDateTime from, final OffsetDateTime to, final String jobType) {
    final var filter = SearchQueryFilterMapper.toGlobalJobStatisticsFilter(from, to, jobType);

    if (filter.isLeft()) {
      final var problem = RequestValidator.createProblemDetail(filter.getLeft());
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    return Either.right(
        SearchQueryBuilders.globalJobStatisticsSearchQuery().filter(filter.get()).build());
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobTypeStatisticsQuery>
      toJobTypeStatisticsQuery(
          final io.camunda.gateway.protocol.model.JobTypeStatisticsQuery request) {
    final Either<List<String>, SearchQueryPage> page =
        toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var filter =
        SearchQueryFilterMapper.toJobTypeStatisticsFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTypeStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobWorkerStatisticsQuery>
      toJobWorkerStatisticsQuery(
          final io.camunda.gateway.protocol.model.JobWorkerStatisticsQuery request) {
    final Either<List<String>, SearchQueryPage> page =
        toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var filter = SearchQueryFilterMapper.toJobWorkerStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobWorkerStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobTimeSeriesStatisticsQuery>
      toJobTimeSeriesStatisticsQuery(
          final io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQuery request) {
    final Either<List<String>, SearchQueryPage> page =
        toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var filter = SearchQueryFilterMapper.toJobTimeSeriesStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTimeSeriesStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, JobErrorStatisticsQuery> toJobErrorStatisticsQuery(
      final io.camunda.gateway.protocol.model.JobErrorStatisticsQuery request) {
    final Either<List<String>, SearchQueryPage> page =
        toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var filter = SearchQueryFilterMapper.toJobErrorStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobErrorStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final io.camunda.gateway.protocol.model.simple.@Nullable ProcessDefinitionSearchQuery query) {
    return toProcessDefinitionQuery(
        query == null
            ? null
            : new ProcessDefinitionSearchQuery()
                .filter(SimpleSearchQueryMapper.toProcessDefinitionFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final @Nullable ProcessDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }

    // Validate isLatestVersion constraints
    final var isLatestVersionValidation =
        validateProcessDefinitionIsLatestVersionConstraints(request);
    if (isLatestVersionValidation.isLeft()) {
      return Either.left(isLatestVersionValidation.getLeft());
    }

    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessDefinitionSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::processDefinition,
            SearchQuerySortRequestMapper::applyProcessDefinitionSortField);
    final var filter =
        SearchQueryFilterMapper.toProcessDefinitionFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
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
        SearchQueryFilterMapper.toProcessDefinitionStatisticsFilter(
            processDefinitionKey, Optional.ofNullable(request.getFilter()).orElse(null));

    if (filter.isLeft()) {
      final var problem = RequestValidator.createProblemDetail(filter.getLeft());
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    return Either.right(filter.get());
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final io.camunda.gateway.protocol.model.simple.@Nullable ProcessInstanceSearchQuery query) {
    return toProcessInstanceQuery(
        query == null
            ? null
            : new ProcessInstanceSearchQuery()
                .filter(SimpleSearchQueryMapper.toProcessInstanceFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final @Nullable ProcessInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessInstanceSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::processInstance,
            SearchQuerySortRequestMapper::applyProcessInstanceSortField);
    final var filter =
        toProcessInstanceFilter(Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, JobQuery> toJobQuery(final JobSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobSearchQuery().build());
    }

    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromJobSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::job,
            SearchQuerySortRequestMapper::applyJobSortField);
    final var filter =
        SearchQueryFilterMapper.toJobFilter(Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::jobSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(final RoleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::role,
            SearchQuerySortRequestMapper::applyRoleSortField);
    final var filter =
        SearchQueryFilterMapper.toRoleFilter(Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleUserSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleUserSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleGroupSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleGroupSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleClientSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleClientSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQuery(
      final GroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::group,
            SearchQuerySortRequestMapper::applyGroupSortField);
    final var filter =
        SearchQueryFilterMapper.toGroupFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupMemberQuery(
      final GroupUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupUserSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupUserSortField);
    final var filter = FilterBuilders.groupMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupMemberQuery(
      final GroupClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupClientSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupClientSortField);
    final var filter = FilterBuilders.groupMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQuery(
      final TenantSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::tenant,
            SearchQuerySortRequestMapper::applyTenantSortField);
    final var filter =
        SearchQueryFilterMapper.toTenantFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantGroupSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantGroupSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantUserSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantUserSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantClientSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantClientSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, MappingRuleQuery> toMappingRuleQuery(
      final MappingRuleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.mappingRuleSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMappingRuleSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::mappingRule,
            SearchQuerySortRequestMapper::applyMappingRuleSortField);
    final var filter =
        SearchQueryFilterMapper.toMappingRuleFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::mappingRuleSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQuery(
      final DecisionDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionDefinitionSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::decisionDefinition,
            SearchQuerySortRequestMapper::applyDecisionDefinitionSortField);
    final var filter =
        SearchQueryFilterMapper.toDecisionDefinitionFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQuery(
      final DecisionRequirementsSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionRequirementsSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::decisionRequirements,
            SearchQuerySortRequestMapper::applyDecisionRequirementsSortField);
    final var filter =
        SearchQueryFilterMapper.toDecisionRequirementsFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionRequirementsSearchQuery);
  }

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toElementInstanceQuery(
      final ElementInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromElementInstanceSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::flowNodeInstance,
            SearchQuerySortRequestMapper::applyElementInstanceSortField);
    final var filter =
        SearchQueryFilterMapper.toElementInstanceFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQuery(
      final DecisionInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionInstanceSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::decisionInstance,
            SearchQuerySortRequestMapper::applyDecisionInstanceSortField);
    final var filter =
        SearchQueryFilterMapper.toDecisionInstanceFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final io.camunda.gateway.protocol.model.simple.@Nullable UserTaskSearchQuery query) {
    return toUserTaskQuery(
        query == null
            ? null
            : new UserTaskSearchQuery()
                .filter(SimpleSearchQueryMapper.toUserTaskFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final @Nullable UserTaskSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::userTask,
            SearchQuerySortRequestMapper::applyUserTaskSortField);
    final var filter =
        SearchQueryFilterMapper.toUserTaskFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQuery(
      final io.camunda.gateway.protocol.model.simple.@Nullable UserTaskVariableFilter filter,
      final io.camunda.gateway.protocol.model.simple.@Nullable SearchQueryPageRequest page,
      final @Nullable List<UserTaskVariableSearchQuerySortRequest> sort) {
    return toUserTaskVariableQuery(
        new UserTaskVariableSearchQueryRequest()
            .filter(SimpleSearchQueryMapper.toUserTaskVariableFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQuery(
      final @Nullable UserTaskVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }

    final var filter =
        SearchQueryFilterMapper.toUserTaskVariableFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskVariableSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);

    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskEffectiveVariableQuery(
      final io.camunda.gateway.protocol.model.simple.@Nullable UserTaskVariableFilter filter,
      final io.camunda.gateway.protocol.model.simple.@Nullable OffsetPagination page,
      final @Nullable List<UserTaskVariableSearchQuerySortRequest> sort) {
    return toUserTaskEffectiveVariableQuery(
        new UserTaskEffectiveVariableSearchQueryRequest()
            .filter(SimpleSearchQueryMapper.toUserTaskVariableFilter(filter))
            .page(SimpleSearchQueryMapper.toOffsetPagination(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskEffectiveVariableQuery(
      final @Nullable UserTaskEffectiveVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }

    final var filter =
        SearchQueryFilterMapper.toUserTaskVariableFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    final var page = toOffsetPagination(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskVariableSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);

    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQuery(
      final io.camunda.gateway.protocol.model.simple.@Nullable VariableFilter filter,
      final io.camunda.gateway.protocol.model.simple.@Nullable SearchQueryPageRequest page,
      final @Nullable List<VariableSearchQuerySortRequest> sort) {
    return toVariableQuery(
        new VariableSearchQuery()
            .filter(SimpleSearchQueryMapper.toVariableFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQuery(
      final @Nullable VariableSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromVariableSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyVariableSortField);
    final var filter =
        SearchQueryFilterMapper.toVariableFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, ClusterVariableQuery> toClusterVariableQuery(
      final ClusterVariableSearchQueryRequest request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.clusterVariableSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromClusterVariableSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::clusterVariable,
            SearchQuerySortRequestMapper::applyClusterVariableSortField);
    final ClusterVariableFilter filter =
        SearchQueryFilterMapper.toClusterVariableFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::clusterVariableSearchQuery);
  }

  public static Either<ProblemDetail, UserQuery> toUserQuery(final UserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }

    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::user,
            SearchQuerySortRequestMapper::applyUserSortField);
    final var filter =
        SearchQueryFilterMapper.toUserFilter(Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final io.camunda.gateway.protocol.model.simple.@Nullable IncidentSearchQuery query) {
    return toIncidentQuery(
        query == null
            ? null
            : new IncidentSearchQuery()
                .filter(SimpleSearchQueryMapper.toIncidentFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final @Nullable IncidentSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }

    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromIncidentSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::incident,
            SearchQuerySortRequestMapper::applyIncidentSortField);
    final var filter = toIncidentFilter(Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQuery(
      final BatchOperationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::batchOperation,
            SearchQuerySortRequestMapper::applyBatchOperationSortField);
    final var filter =
        SearchQueryFilterMapper.toBatchOperationFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationQuery);
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQuery(
      final BatchOperationItemSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationItemQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationItemSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::batchOperationItem,
            SearchQuerySortRequestMapper::applyBatchOperationItemSortField);
    final var filter =
        SearchQueryFilterMapper.toBatchOperationItemFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationItemQuery);
  }

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQuery(
      final AuthorizationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.authorizationSearchQuery().build());
    }

    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromAuthorizationSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::authorization,
            SearchQuerySortRequestMapper::applyAuthorizationSortField);
    final var filter =
        SearchQueryFilterMapper.toAuthorizationFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::authorizationSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toAuditLogQuery(
      final AuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }

    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromAuditLogSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter =
        SearchQueryFilterMapper.toAuditLogFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toUserTaskAuditLogQuery(
      final UserTaskAuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }

    final var filter =
        SearchQueryFilterMapper.toUserTaskAuditLogFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskAuditLogSearchRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);

    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQuery(
      final MessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.messageSubscriptionSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMessageSubscriptionSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::messageSubscription,
            SearchQuerySortRequestMapper::applyMessageSubscriptionSortField);
    final var filter =
        SearchQueryFilterMapper.toMessageSubscriptionFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::messageSubscriptionSearchQuery);
  }

  public static Either<
          ProblemDetail,
          io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery>
      toProcessDefinitionMessageSubscriptionStatisticsQuery(
          final ProcessDefinitionMessageSubscriptionStatisticsQuery request) {
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.processDefinitionMessageSubscriptionStatisticsQuery().build());
    }
    final Either<List<String>, SearchQueryPage> page =
        toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var filter =
        SearchQueryFilterMapper.toMessageSubscriptionFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(
        filter,
        Either.right(null),
        page,
        SearchQueryBuilders::processDefinitionMessageSubscriptionStatisticsQuery);
  }

  public static Either<ProblemDetail, CorrelatedMessageSubscriptionQuery>
      toCorrelatedMessageSubscriptionQuery(final CorrelatedMessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.correlatedMessageSubscriptionSearchQuery().build());
    }
    final var page = toSearchQueryPage(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromCorrelatedMessageSubscriptionSearchQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::correlatedMessageSubscription,
            SearchQuerySortRequestMapper::applyCorrelatedMessageSubscriptionSortField);
    final var filter =
        SearchQueryFilterMapper.toCorrelatedMessageSubscriptionFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::correlatedMessageSubscriptionSearchQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery>
      toProcessDefinitionInstanceStatisticsQuery(
          final ProcessDefinitionInstanceStatisticsQuery request) {
    final var filter =
        FilterBuilders.processInstance().states(ProcessInstanceState.ACTIVE.name()).build();
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.processDefinitionInstanceStatisticsQuery().filter(filter).build());
    }

    final var page = toOffsetPagination(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessDefinitionInstanceStatisticsQuerySortRequest(
                Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::processDefinitionInstanceStatistics,
            SearchQuerySortRequestMapper::applyProcessDefinitionInstanceStatisticsSortField);
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionInstanceStatisticsQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery>
      toProcessDefinitionInstanceVersionStatisticsQuery(
          final ProcessDefinitionInstanceVersionStatisticsQuery request) {
    if (request == null || Optional.ofNullable(request.getFilter()).orElse(null) == null) {
      final var problem =
          RequestValidator.createProblemDetail(
              List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }

    final var page = toOffsetPagination(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper
                .fromProcessDefinitionInstanceVersionStatisticsQuerySortRequest(
                    Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::processDefinitionInstanceVersionStatistics,
            SearchQuerySortRequestMapper::applyProcessDefinitionInstanceVersionStatisticsSortField);
    final var filter =
        toProcessDefinitionInstanceVersionStatisticsFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionInstanceVersionStatisticsQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery>
      toIncidentProcessInstanceStatisticsByErrorQuery(
          final IncidentProcessInstanceStatisticsByErrorQuery request) {
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.incidentProcessInstanceStatisticsByErrorQuery().build());
    }

    final var page = toOffsetPagination(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper
                .fromIncidentProcessInstanceStatisticsByErrorQuerySortRequest(
                    Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::incidentProcessInstanceStatisticsByError,
            SearchQuerySortRequestMapper::applyIncidentProcessInstanceStatisticsByErrorSortField);
    final var filter = FilterBuilders.incident().build();
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::incidentProcessInstanceStatisticsByErrorQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery>
      toIncidentProcessInstanceStatisticsByDefinitionQuery(
          final IncidentProcessInstanceStatisticsByDefinitionQuery request) {
    final var page = toOffsetPagination(Optional.ofNullable(request.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper
                .fromIncidentProcessInstanceStatisticsByDefinitionQuerySortRequest(
                    Optional.ofNullable(request.getSort()).orElse(null)),
            SortOptionBuilders::incidentProcessInstanceStatisticsByDefinition,
            SearchQuerySortRequestMapper
                ::applyIncidentProcessInstanceStatisticsByDefinitionSortField);

    final var filter =
        SearchQueryFilterMapper.toIncidentProcessInstanceStatisticsByDefinitionFilter(
            Optional.ofNullable(request.getFilter()).orElse(null));
    return buildSearchQuery(
        filter,
        sort,
        page,
        SearchQueryBuilders::incidentProcessInstanceStatisticsByDefinitionQuery);
  }

  public static Either<ProblemDetail, GlobalListenerQuery> toGlobalTaskListenerQuery(
      final GlobalTaskListenerSearchQueryRequest request) {
    // Create empty request if not provided, then pass through normal transformation to apply
    // default values
    final GlobalTaskListenerSearchQueryRequest actualRequest =
        request == null ? new GlobalTaskListenerSearchQueryRequest() : request;

    final var page =
        SearchQueryRequestMapper.toSearchQueryPage(
            Optional.ofNullable(actualRequest.getPage()).orElse(null));
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGlobalTaskListenerSearchQuerySortRequest(
                Optional.ofNullable(actualRequest.getSort()).orElse(null)),
            SortOptionBuilders::globalListener,
            SearchQuerySortRequestMapper::applyGlobalTaskListenerSortField);
    final var filter =
        SearchQueryFilterMapper.toGlobalTaskListenerFilter(
            Optional.ofNullable(actualRequest.getFilter()).orElse(null));
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::globalListenerSearchQuery);
  }

  private static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final @Nullable SearchQueryPageRequest requestedPage) {
    if (requestedPage == null) {
      return Either.right(null);
    }

    final List<String> violations = new ArrayList<>();
    final Integer limit = Optional.ofNullable(requestedPage.getLimit()).orElse(null);
    final String before = Optional.ofNullable(requestedPage.getBefore()).orElse(null);
    final String after = Optional.ofNullable(requestedPage.getAfter()).orElse(null);
    final Integer from = Optional.ofNullable(requestedPage.getFrom()).orElse(null);

    if (limit != null && limit < 0) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "page.limit", limit, "a non-negative number"));
    }

    if (from != null && from < 0) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "page.from", from, "a non-negative number"));
    }

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    if (before != null) {
      return Either.right(SearchQueryPage.of((p) -> p.size(limit).before(before)));
    } else if (after != null) {
      return Either.right(SearchQueryPage.of((p) -> p.size(limit).after(after)));
    } else if (from != null) {
      return Either.right(SearchQueryPage.of((p) -> p.size(limit).from(from)));
    } else {
      return Either.right(SearchQueryPage.of((p) -> p.size(limit)));
    }
  }

  private static Either<ProblemDetail, Void> validateProcessDefinitionIsLatestVersionConstraints(
      final ProcessDefinitionSearchQuery request) {
    final List<String> violations = new ArrayList<>();

    // Check if isLatestVersion filter is set to true
    final var filter = Optional.ofNullable(request.getFilter()).orElse(null);
    if (filter == null
        || Optional.ofNullable(filter.getIsLatestVersion()).isEmpty()
        || !Optional.ofNullable(filter.getIsLatestVersion()).get()) {
      return Either.right(null);
    }

    // Validate pagination: only 'after' and 'limit' are allowed
    final var page = Optional.ofNullable(request.getPage()).orElse(null);
    if (page != null) {
      if (Optional.ofNullable(page.getBefore()).isPresent()) {
        violations.add(
            ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION.formatted("before"));
      }
      if (Optional.ofNullable(page.getFrom()).isPresent()) {
        violations.add(
            ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION.formatted("from"));
      }
    }

    // Validate sorting: only 'processDefinitionId' and 'tenantId' are allowed
    final var sort = Optional.ofNullable(request.getSort()).orElse(null);
    if (sort != null && !sort.isEmpty()) {
      for (final var sortRequest : sort) {
        final var field = sortRequest.getField();
        if (field != null
            && field
                != io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuerySortRequest
                    .FieldEnum.PROCESS_DEFINITION_ID
            && field
                != io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuerySortRequest
                    .FieldEnum.TENANT_ID) {
          violations.add(
              ERROR_MESSAGE_UNSUPPORTED_SORT_FIELD_WITH_IS_LATEST_VERSION.formatted(field));
        }
      }
    }

    if (!violations.isEmpty()) {
      final var problem = RequestValidator.createProblemDetail(violations);
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }

    return Either.right(null);
  }

  private static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final @Nullable CursorForwardPagination requestedPage) {
    if (requestedPage == null) {
      return Either.right(null);
    }

    final List<String> violations = new ArrayList<>();
    final Integer limit = Optional.ofNullable(requestedPage.getLimit()).orElse(null);
    final String after = Optional.ofNullable(requestedPage.getAfter()).orElse(null);

    if (limit != null && limit < 0) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "page.limit", limit, "a non-negative number"));
    }

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(SearchQueryPage.of((p) -> p.size(limit).after(after)));
  }

  private static Either<List<String>, SearchQueryPage> toOffsetPagination(
      final @Nullable OffsetPagination requestedPage) {

    if (requestedPage == null) {
      return Either.right(null);
    }

    final List<String> violations = new ArrayList<>();
    final Integer limit = Optional.ofNullable(requestedPage.getLimit()).orElse(null);
    final Integer from = Optional.ofNullable(requestedPage.getFrom()).orElse(null);

    if (limit != null && limit < 0) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "page.limit", limit, "a non-negative number"));
    }

    if (from != null && from < 0) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "page.from", from, "a non-negative number"));
    }

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(SearchQueryPage.of((p) -> p.size(limit).from(from)));
  }

  private static <
          T,
          B extends TypedSearchQueryBuilder<T, B, F, S>,
          F extends FilterBase,
          S extends SortOption>
      Either<ProblemDetail, T> buildSearchQuery(
          final @Nullable F filter,
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
}
