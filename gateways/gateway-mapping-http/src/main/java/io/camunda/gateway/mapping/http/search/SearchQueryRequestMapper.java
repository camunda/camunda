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
import io.camunda.search.filter.VariableFilter;
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
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
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
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
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
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobTypeStatisticsSearchQuery().build());
    }
    final Either<List<String>, SearchQueryPage> page =
        request.getPage() == null
            ? Either.right(null)
            : Either.right(toSearchQueryPage(request.getPage()));
    final var filter = SearchQueryFilterMapper.toJobTypeStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTypeStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final io.camunda.gateway.protocol.model.simple.ProcessDefinitionFilter filter,
      final io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest page,
      final List<ProcessDefinitionSearchQuerySortRequest> sort) {
    return toProcessDefinitionQuery(
        new ProcessDefinitionSearchQuery()
            .filter(SimpleSearchQueryMapper.toProcessDefinitionFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final ProcessDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessDefinitionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::processDefinition,
            SearchQuerySortRequestMapper::applyProcessDefinitionSortField);
    final var filter = SearchQueryFilterMapper.toProcessDefinitionFilter(request.getFilter());
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
            processDefinitionKey, request.getFilter());

    if (filter.isLeft()) {
      final var problem = RequestValidator.createProblemDetail(filter.getLeft());
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    return Either.right(filter.get());
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter filter,
      final io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest page,
      final List<ProcessInstanceSearchQuerySortRequest> sort) {
    return toProcessInstanceQuery(
        new ProcessInstanceSearchQuery()
            .filter(SimpleSearchQueryMapper.toProcessInstanceFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final ProcessInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::processInstance,
            SearchQuerySortRequestMapper::applyProcessInstanceSortField);
    final var filter = toProcessInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, JobQuery> toJobQuery(final JobSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromJobSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::job,
            SearchQuerySortRequestMapper::applyJobSortField);
    final var filter = SearchQueryFilterMapper.toJobFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::jobSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(final RoleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::role,
            SearchQuerySortRequestMapper::applyRoleSortField);
    final var filter = SearchQueryFilterMapper.toRoleFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleUserSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleGroupSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromRoleClientSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::group,
            SearchQuerySortRequestMapper::applyGroupSortField);
    final var filter = SearchQueryFilterMapper.toGroupFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupMemberQuery(
      final GroupUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupUserSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGroupClientSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::tenant,
            SearchQuerySortRequestMapper::applyTenantSortField);
    final var filter = SearchQueryFilterMapper.toTenantFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantGroupSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantUserSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromTenantClientSearchQuerySortRequest(request.getSort()),
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMappingRuleSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::mappingRule,
            SearchQuerySortRequestMapper::applyMappingRuleSortField);
    final var filter = SearchQueryFilterMapper.toMappingRuleFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::mappingRuleSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQuery(
      final DecisionDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionDefinitionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionDefinition,
            SearchQuerySortRequestMapper::applyDecisionDefinitionSortField);
    final var filter = SearchQueryFilterMapper.toDecisionDefinitionFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQuery(
      final DecisionRequirementsSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionRequirementsSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionRequirements,
            SearchQuerySortRequestMapper::applyDecisionRequirementsSortField);
    final var filter = SearchQueryFilterMapper.toDecisionRequirementsFilter(request.getFilter());
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
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromElementInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::flowNodeInstance,
            SearchQuerySortRequestMapper::applyElementInstanceSortField);
    final var filter = SearchQueryFilterMapper.toElementInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQuery(
      final DecisionInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromDecisionInstanceSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::decisionInstance,
            SearchQuerySortRequestMapper::applyDecisionInstanceSortField);
    final var filter = SearchQueryFilterMapper.toDecisionInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final io.camunda.gateway.protocol.model.simple.UserTaskFilter filter,
      final io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest page,
      final List<UserTaskSearchQuerySortRequest> sort) {
    return toUserTaskQuery(
        new UserTaskSearchQuery()
            .filter(SimpleSearchQueryMapper.toUserTaskFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final UserTaskSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::userTask,
            SearchQuerySortRequestMapper::applyUserTaskSortField);
    final var filter = SearchQueryFilterMapper.toUserTaskFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQuery(
      final io.camunda.gateway.protocol.model.simple.UserTaskVariableFilter filter,
      final io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest page,
      final List<UserTaskVariableSearchQuerySortRequest> sort) {
    return toUserTaskVariableQuery(
        new UserTaskVariableSearchQueryRequest()
            .filter(SimpleSearchQueryMapper.toUserTaskVariableFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQuery(
      final UserTaskVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }

    final var filter = SearchQueryFilterMapper.toUserTaskVariableFilter(request.getFilter());
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskVariableSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);

    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQuery(
      final io.camunda.gateway.protocol.model.simple.VariableFilter filter,
      final io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest page,
      final List<VariableSearchQuerySortRequest> sort) {
    return toVariableQuery(
        new VariableSearchQuery()
            .filter(SimpleSearchQueryMapper.toVariableFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQuery(
      final VariableSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromVariableSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyVariableSortField);
    final VariableFilter filter = SearchQueryFilterMapper.toVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, ClusterVariableQuery> toClusterVariableQuery(
      final ClusterVariableSearchQueryRequest request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.clusterVariableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromClusterVariableSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::clusterVariable,
            SearchQuerySortRequestMapper::applyClusterVariableSortField);
    final ClusterVariableFilter filter =
        SearchQueryFilterMapper.toClusterVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::clusterVariableSearchQuery);
  }

  public static Either<ProblemDetail, UserQuery> toUserQuery(final UserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::user,
            SearchQuerySortRequestMapper::applyUserSortField);
    final var filter = SearchQueryFilterMapper.toUserFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final io.camunda.gateway.protocol.model.simple.IncidentFilter filter,
      final io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest page,
      final List<IncidentSearchQuerySortRequest> sort) {
    return toIncidentQuery(
        new IncidentSearchQuery()
            .filter(SimpleSearchQueryMapper.toIncidentFilter(filter))
            .page(SimpleSearchQueryMapper.toPageRequest(page))
            .sort(sort == null ? List.of() : sort));
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final IncidentSearchQuery request) {

    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromIncidentSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::incident,
            SearchQuerySortRequestMapper::applyIncidentSortField);
    final var filter = toIncidentFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQuery(
      final BatchOperationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::batchOperation,
            SearchQuerySortRequestMapper::applyBatchOperationSortField);
    final var filter = SearchQueryFilterMapper.toBatchOperationFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationQuery);
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQuery(
      final BatchOperationItemSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationItemQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromBatchOperationItemSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::batchOperationItem,
            SearchQuerySortRequestMapper::applyBatchOperationItemSortField);
    final var filter = SearchQueryFilterMapper.toBatchOperationItemFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationItemQuery);
  }

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQuery(
      final AuthorizationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.authorizationSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromAuthorizationSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::authorization,
            SearchQuerySortRequestMapper::applyAuthorizationSortField);
    final var filter = SearchQueryFilterMapper.toAuthorizationFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::authorizationSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toAuditLogQuery(
      final AuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromAuditLogSearchQuerySortRequest(request.getSort()),
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = SearchQueryFilterMapper.toAuditLogFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toUserTaskAuditLogQuery(
      final UserTaskAuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }

    final var filter = SearchQueryFilterMapper.toUserTaskAuditLogFilter(request.getFilter());
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromUserTaskAuditLogSearchRequest(request.getSort()),
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);

    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQuery(
      final MessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.messageSubscriptionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromMessageSubscriptionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::messageSubscription,
            SearchQuerySortRequestMapper::applyMessageSubscriptionSortField);
    final var filter = SearchQueryFilterMapper.toMessageSubscriptionFilter(request.getFilter());
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
        request.getPage() == null
            ? Either.right(null)
            : Either.right(toSearchQueryPage(request.getPage()));
    final var filter = SearchQueryFilterMapper.toMessageSubscriptionFilter(request.getFilter());
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
    final var page = toSearchQueryPage(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromCorrelatedMessageSubscriptionSearchQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::correlatedMessageSubscription,
            SearchQuerySortRequestMapper::applyCorrelatedMessageSubscriptionSortField);
    final var filter =
        SearchQueryFilterMapper.toCorrelatedMessageSubscriptionFilter(request.getFilter());
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

    final var page = toOffsetPagination(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromProcessDefinitionInstanceStatisticsQuerySortRequest(
                request.getSort()),
            SortOptionBuilders::processDefinitionInstanceStatistics,
            SearchQuerySortRequestMapper::applyProcessDefinitionInstanceStatisticsSortField);
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionInstanceStatisticsQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery>
      toProcessDefinitionInstanceVersionStatisticsQuery(
          final ProcessDefinitionInstanceVersionStatisticsQuery request) {
    if (request == null || request.getFilter() == null) {
      final var problem =
          RequestValidator.createProblemDetail(
              List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }

    final var page = toOffsetPagination(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper
                .fromProcessDefinitionInstanceVersionStatisticsQuerySortRequest(request.getSort()),
            SortOptionBuilders::processDefinitionInstanceVersionStatistics,
            SearchQuerySortRequestMapper::applyProcessDefinitionInstanceVersionStatisticsSortField);
    final var filter = toProcessDefinitionInstanceVersionStatisticsFilter(request.getFilter());
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

    final var page = toOffsetPagination(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper
                .fromIncidentProcessInstanceStatisticsByErrorQuerySortRequest(request.getSort()),
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
    final var page = toOffsetPagination(request.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper
                .fromIncidentProcessInstanceStatisticsByDefinitionQuerySortRequest(
                    request.getSort()),
            SortOptionBuilders::incidentProcessInstanceStatisticsByDefinition,
            SearchQuerySortRequestMapper
                ::applyIncidentProcessInstanceStatisticsByDefinitionSortField);

    final var filter =
        SearchQueryFilterMapper.toIncidentProcessInstanceStatisticsByDefinitionFilter(
            request.getFilter());
    return buildSearchQuery(
        filter,
        sort,
        page,
        SearchQueryBuilders::incidentProcessInstanceStatisticsByDefinitionQuery);
  }

  private static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final SearchQueryPageRequest requestedPage) {
    if (requestedPage == null) {
      return Either.right(null);
    }

    return switch (requestedPage) {
      case final CursorBackwardPagination req -> Either.right(toSearchQueryPage(req));
      case final CursorForwardPagination req -> Either.right(toSearchQueryPage(req));
      case final OffsetPagination req -> Either.right(toSearchQueryPage(req));
      case final LimitPagination req -> Either.right(toSearchQueryPage(req));
      default -> Either.left(List.of(ERROR_SEARCH_UNKNOWN_PAGE_TYPE));
    };
  }

  private static SearchQueryPage toSearchQueryPage(final CursorBackwardPagination requestedPage) {
    return SearchQueryPage.of(
        (p) -> p.size(requestedPage.getLimit()).before(requestedPage.getBefore()));
  }

  private static SearchQueryPage toSearchQueryPage(final CursorForwardPagination requestedPage) {
    return SearchQueryPage.of(
        (p) -> p.size(requestedPage.getLimit()).after(requestedPage.getAfter()));
  }

  private static SearchQueryPage toSearchQueryPage(final OffsetPagination requestedPage) {
    return SearchQueryPage.of(
        (p) -> p.size(requestedPage.getLimit()).from(requestedPage.getFrom()));
  }

  private static SearchQueryPage toSearchQueryPage(final LimitPagination requestedPage) {
    return SearchQueryPage.of((p) -> p.size(requestedPage.getLimit()));
  }

  private static Either<List<String>, SearchQueryPage> toOffsetPagination(
      final OffsetPagination requestedPage) {

    if (requestedPage == null) {
      return Either.right(null);
    }

    // Delegate to the existing mapping
    return Either.right(toSearchQueryPage(requestedPage));
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
}
