/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.RequestMapper.getResult;
import static io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper.toProcessDefinitionInstanceVersionStatisticsFilter;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.*;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobErrorStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobTimeSeriesStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobTypeStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobWorkerStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionElementStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.validator.RequestValidator;
import io.camunda.gateway.protocol.model.*;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GlobalListenerFilter;
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
    final Either<List<String>, SearchQueryPage> page = toSearchQueryPage(request.getPage());
    final var filter = SearchQueryFilterMapper.toJobTypeStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTypeStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobWorkerStatisticsQuery>
      toJobWorkerStatisticsQuery(
          final io.camunda.gateway.protocol.model.JobWorkerStatisticsQuery request) {
    final Either<List<String>, SearchQueryPage> page = toSearchQueryPage(request.getPage());
    final var filter = SearchQueryFilterMapper.toJobWorkerStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobWorkerStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobTimeSeriesStatisticsQuery>
      toJobTimeSeriesStatisticsQuery(
          final io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQuery request) {
    final Either<List<String>, SearchQueryPage> page = toSearchQueryPage(request.getPage());
    final var filter = SearchQueryFilterMapper.toJobTimeSeriesStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTimeSeriesStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, JobErrorStatisticsQuery> toJobErrorStatisticsQuery(
      final io.camunda.gateway.protocol.model.JobErrorStatisticsQuery request) {
    final Either<List<String>, SearchQueryPage> page = toSearchQueryPage(request.getPage());
    final var filter = SearchQueryFilterMapper.toJobErrorStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobErrorStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final io.camunda.gateway.protocol.model.simple.ProcessDefinitionSearchQuery query) {
    return toProcessDefinitionQuery(
        query == null
            ? null
            : new ProcessDefinitionSearchQuery()
                .filter(SimpleSearchQueryMapper.toProcessDefinitionFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQuery(
      final ProcessDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }

    // Validate isLatestVersion constraints
    final var isLatestVersionValidation =
        validateProcessDefinitionIsLatestVersionConstraints(request);
    if (isLatestVersionValidation.isLeft()) {
      return Either.left(isLatestVersionValidation.getLeft());
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
      final io.camunda.gateway.protocol.model.simple.ProcessInstanceSearchQuery query) {
    return toProcessInstanceQuery(
        query == null
            ? null
            : new ProcessInstanceSearchQuery()
                .filter(SimpleSearchQueryMapper.toProcessInstanceFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQuery(
      final ProcessInstanceSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toProcessInstanceQuery(request);
  }

  public static Either<ProblemDetail, JobQuery> toJobQuery(final JobSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toJobQuery(request);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQuery(final RoleSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toRoleQuery(request);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleUserSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toRoleMemberQuery(request);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleGroupSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toRoleMemberQuery(request);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleMemberQuery(
      final RoleClientSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toRoleMemberQuery(request);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQuery(
      final GroupSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toGroupQuery(request);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupMemberQuery(
      final GroupUserSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toGroupMemberQuery(request);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupMemberQuery(
      final GroupClientSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toGroupMemberQuery(request);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQuery(
      final TenantSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toTenantQuery(request);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantGroupSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toTenantMemberQuery(request);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantUserSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toTenantMemberQuery(request);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantMemberQuery(
      final TenantClientSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toTenantMemberQuery(request);
  }

  public static Either<ProblemDetail, MappingRuleQuery> toMappingRuleQuery(
      final MappingRuleSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toMappingRuleQuery(request);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQuery(
      final DecisionDefinitionSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toDecisionDefinitionQuery(request);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQuery(
      final DecisionRequirementsSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toDecisionRequirementsQuery(request);
  }

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toElementInstanceQuery(
      final ElementInstanceSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toElementInstanceQuery(request);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQuery(
      final DecisionInstanceSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toDecisionInstanceQuery(request);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final io.camunda.gateway.protocol.model.simple.UserTaskSearchQuery query) {
    return toUserTaskQuery(
        query == null
            ? null
            : new UserTaskSearchQuery()
                .filter(SimpleSearchQueryMapper.toUserTaskFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQuery(
      final UserTaskSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toUserTaskQuery(request);
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
    return GeneratedSearchQueryRequestMapper.toUserTaskVariableQuery(request);
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
    return GeneratedSearchQueryRequestMapper.toVariableQuery(request);
  }

  public static Either<ProblemDetail, ClusterVariableQuery> toClusterVariableQuery(
      final ClusterVariableSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toClusterVariableQuery(request);
  }

  public static Either<ProblemDetail, UserQuery> toUserQuery(final UserSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toUserQuery(request);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final io.camunda.gateway.protocol.model.simple.IncidentSearchQuery query) {
    return toIncidentQuery(
        query == null
            ? null
            : new IncidentSearchQuery()
                .filter(SimpleSearchQueryMapper.toIncidentFilter(query.getFilter()))
                .page(SimpleSearchQueryMapper.toPageRequest(query.getPage()))
                .sort(query.getSort() == null ? List.of() : query.getSort()));
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQuery(
      final IncidentSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toIncidentQuery(request);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQuery(
      final BatchOperationSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toBatchOperationQuery(request);
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQuery(
      final BatchOperationItemSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toBatchOperationItemQuery(request);
  }

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQuery(
      final AuthorizationSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toAuthorizationQuery(request);
  }

  public static Either<ProblemDetail, AuditLogQuery> toAuditLogQuery(
      final AuditLogSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toAuditLogQuery(request);
  }

  public static Either<ProblemDetail, AuditLogQuery> toUserTaskAuditLogQuery(
      final UserTaskAuditLogSearchQueryRequest request) {
    return GeneratedSearchQueryRequestMapper.toUserTaskAuditLogQuery(request);
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQuery(
      final MessageSubscriptionSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toMessageSubscriptionQuery(request);
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
    final Either<List<String>, SearchQueryPage> page = toSearchQueryPage(request.getPage());
    final var filter = SearchQueryFilterMapper.toMessageSubscriptionFilter(request.getFilter());
    return buildSearchQuery(
        filter,
        Either.right(null),
        page,
        SearchQueryBuilders::processDefinitionMessageSubscriptionStatisticsQuery);
  }

  public static Either<ProblemDetail, CorrelatedMessageSubscriptionQuery>
      toCorrelatedMessageSubscriptionQuery(final CorrelatedMessageSubscriptionSearchQuery request) {
    return GeneratedSearchQueryRequestMapper.toCorrelatedMessageSubscriptionQuery(request);
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

  public static Either<ProblemDetail, GlobalListenerQuery> toGlobalTaskListenerQuery(
      final GlobalTaskListenerSearchQueryRequest request) {
    // Create empty request if not provided, then pass through normal transformation to apply
    // default values
    final GlobalTaskListenerSearchQueryRequest actualRequest =
        request == null ? new GlobalTaskListenerSearchQueryRequest() : request;

    final var page = SearchQueryRequestMapper.toSearchQueryPage(actualRequest.getPage());
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            SearchQuerySortRequestMapper.fromGlobalTaskListenerSearchQuerySortRequest(
                actualRequest.getSort()),
            SortOptionBuilders::globalListener,
            SearchQuerySortRequestMapper::applyGlobalTaskListenerSortField);
    final GlobalListenerFilter filter =
        SearchQueryFilterMapper.toGlobalTaskListenerFilter(actualRequest.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::globalListenerSearchQuery);
  }

  static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final SearchQueryPageRequest requestedPage) {
    if (requestedPage == null) {
      return Either.right(null);
    }

    final List<String> violations = new ArrayList<>();
    validatePageAttributes(requestedPage, violations);

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return switch (requestedPage) {
      case final CursorBackwardPagination req -> Either.right(innerToSearchQueryPage(req));
      case final CursorForwardPagination req -> Either.right(innerToSearchQueryPage(req));
      case final OffsetPagination req -> Either.right(innerToSearchQueryPage(req));
      case final LimitPagination req -> Either.right(innerToSearchQueryPage(req));
      default -> Either.left(List.of(ERROR_SEARCH_UNKNOWN_PAGE_TYPE));
    };
  }

  private static void validatePageAttributes(
      final SearchQueryPageRequest requestedPage, final List<String> violations) {
    final Integer limit =
        switch (requestedPage) {
          case final CursorBackwardPagination req -> req.getLimit();
          case final CursorForwardPagination req -> req.getLimit();
          case final OffsetPagination req -> req.getLimit();
          case final LimitPagination req -> req.getLimit();
          default -> null;
        };

    if (limit != null && limit < 0) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "page.limit", limit, "a non-negative number"));
    }

    if (requestedPage instanceof final OffsetPagination req) {
      final Integer from = req.getFrom();
      if (from != null && from < 0) {
        violations.add(
            ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                "page.from", from, "a non-negative number"));
      }
    }
  }

  private static SearchQueryPage innerToSearchQueryPage(
      final CursorBackwardPagination requestedPage) {
    return SearchQueryPage.of(
        (p) -> p.size(requestedPage.getLimit()).before(requestedPage.getBefore()));
  }

  private static SearchQueryPage innerToSearchQueryPage(
      final CursorForwardPagination requestedPage) {
    return SearchQueryPage.of(
        (p) -> p.size(requestedPage.getLimit()).after(requestedPage.getAfter()));
  }

  private static SearchQueryPage innerToSearchQueryPage(final OffsetPagination requestedPage) {
    return SearchQueryPage.of(
        (p) -> p.size(requestedPage.getLimit()).from(requestedPage.getFrom()));
  }

  private static SearchQueryPage innerToSearchQueryPage(final LimitPagination requestedPage) {
    return SearchQueryPage.of((p) -> p.size(requestedPage.getLimit()));
  }

  /**
   * Overload that accepts flat page fields (no polymorphic subtypes). Determines pagination type
   * from which fields are non-null.
   */
  static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final Integer limit, final Integer from, final String after, final String before) {
    if (limit == null && from == null && after == null && before == null) {
      return Either.right(null);
    }

    final List<String> violations = new ArrayList<>();
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

    // Ensure only one pagination mode is used
    final int modeCount =
        (from != null ? 1 : 0) + (after != null ? 1 : 0) + (before != null ? 1 : 0);
    if (modeCount > 1) {
      violations.add(ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(List.of("from", "after", "before")));
    }

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    if (before != null) {
      return Either.right(SearchQueryPage.of(p -> p.size(limit).before(before)));
    } else if (after != null) {
      return Either.right(SearchQueryPage.of(p -> p.size(limit).after(after)));
    } else if (from != null) {
      return Either.right(SearchQueryPage.of(p -> p.size(limit).from(from)));
    } else {
      return Either.right(SearchQueryPage.of(p -> p.size(limit)));
    }
  }

  private static Either<ProblemDetail, Void> validateProcessDefinitionIsLatestVersionConstraints(
      final ProcessDefinitionSearchQuery request) {
    final List<String> violations = new ArrayList<>();

    // Check if isLatestVersion filter is set to true
    final var filter = request.getFilter();
    if (filter == null || filter.getIsLatestVersion() == null || !filter.getIsLatestVersion()) {
      return Either.right(null);
    }

    // Validate pagination: only 'after' and 'limit' are allowed
    final var page = request.getPage();
    if (page != null) {
      if (page instanceof CursorBackwardPagination) {
        violations.add(
            ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION.formatted("before"));
      } else if (page instanceof OffsetPagination) {
        violations.add(
            ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION.formatted("from"));
      }
    }

    // Validate sorting: only 'processDefinitionId' and 'tenantId' are allowed
    final var sort = request.getSort();
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

  private static Either<List<String>, SearchQueryPage> toOffsetPagination(
      final OffsetPagination requestedPage) {

    if (requestedPage == null) {
      return Either.right(null);
    }

    final List<String> violations = new ArrayList<>();
    validatePageAttributes(requestedPage, violations);

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    // Delegate to the existing mapping
    return Either.right(innerToSearchQueryPage(requestedPage));
  }

  static <
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

  static <
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

  // --- Statistics strict-contract overloads ---

  // Pattern 1: filter only, no page/sort
  public static Either<ProblemDetail, ProcessDefinitionStatisticsFilter>
      toProcessDefinitionStatisticsQuery(
          final long processDefinitionKey,
          final GeneratedProcessDefinitionElementStatisticsQueryStrictContract request) {
    if (request == null) {
      return Either.right(
          new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey).build());
    }
    final var filter =
        SearchQueryFilterMapper.toProcessDefinitionStatisticsFilter(
            processDefinitionKey, request.filter());
    if (filter.isLeft()) {
      final var problem = RequestValidator.createProblemDetail(filter.getLeft());
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    return Either.right(filter.get());
  }

  // Pattern 2: CursorForwardPagination + filter, no sort
  public static Either<ProblemDetail, io.camunda.search.query.JobTypeStatisticsQuery>
      toJobTypeStatisticsQuery(final GeneratedJobTypeStatisticsQueryStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobTypeStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter = SearchQueryFilterMapper.toJobTypeStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTypeStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobWorkerStatisticsQuery>
      toJobWorkerStatisticsQuery(final GeneratedJobWorkerStatisticsQueryStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobWorkerStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter = SearchQueryFilterMapper.toJobWorkerStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobWorkerStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobTimeSeriesStatisticsQuery>
      toJobTimeSeriesStatisticsQuery(
          final GeneratedJobTimeSeriesStatisticsQueryStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobTimeSeriesStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter = SearchQueryFilterMapper.toJobTimeSeriesStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTimeSeriesStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, JobErrorStatisticsQuery> toJobErrorStatisticsQuery(
      final GeneratedJobErrorStatisticsQueryStrictContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobErrorStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter = SearchQueryFilterMapper.toJobErrorStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobErrorStatisticsSearchQuery);
  }

  public static Either<
          ProblemDetail,
          io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery>
      toProcessDefinitionMessageSubscriptionStatisticsQuery(
          final GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract
              request) {
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.processDefinitionMessageSubscriptionStatisticsQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter = SearchQueryFilterMapper.toMessageSubscriptionFilter(request.filter());
    return buildSearchQuery(
        filter,
        Either.right(null),
        page,
        SearchQueryBuilders::processDefinitionMessageSubscriptionStatisticsQuery);
  }

  // Pattern 3: OffsetPagination + sort (+ optional filter)
  public static Either<
          ProblemDetail, io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery>
      toProcessDefinitionInstanceStatisticsQuery(
          final GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract request) {
    final var filter =
        FilterBuilders.processInstance().states(ProcessInstanceState.ACTIVE.name()).build();
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.processDefinitionInstanceStatisticsQuery().filter(filter).build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), null, null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::processDefinitionInstanceStatistics,
            SearchQuerySortRequestMapper::applyProcessDefinitionInstanceStatisticsSortField);
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionInstanceStatisticsQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery>
      toProcessDefinitionInstanceVersionStatisticsQuery(
          final GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract request) {
    if (request == null || request.filter() == null) {
      final var problem =
          RequestValidator.createProblemDetail(
              List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), null, null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::processDefinitionInstanceVersionStatistics,
            SearchQuerySortRequestMapper::applyProcessDefinitionInstanceVersionStatisticsSortField);
    final var filter =
        SearchQueryFilterMapper.toProcessDefinitionInstanceVersionStatisticsFilter(
            request.filter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionInstanceVersionStatisticsQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery>
      toIncidentProcessInstanceStatisticsByErrorQuery(
          final GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract request) {
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.incidentProcessInstanceStatisticsByErrorQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), null, null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::incidentProcessInstanceStatisticsByError,
            SearchQuerySortRequestMapper::applyIncidentProcessInstanceStatisticsByErrorSortField);
    final var filter = FilterBuilders.incident().build();
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::incidentProcessInstanceStatisticsByErrorQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery>
      toIncidentProcessInstanceStatisticsByDefinitionQuery(
          final GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract request) {
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), null, null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var sortRequests =
        request.sort() != null
            ? request.sort().stream()
                .map(
                    s ->
                        new SearchQuerySortRequest(
                            s.field().getValue(), s.order() != null ? s.order().getValue() : null))
                .toList()
            : java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::incidentProcessInstanceStatisticsByDefinition,
            SearchQuerySortRequestMapper
                ::applyIncidentProcessInstanceStatisticsByDefinitionSortField);
    final var filter =
        SearchQueryFilterMapper.toIncidentProcessInstanceStatisticsByDefinitionFilter(
            request.filter());
    return buildSearchQuery(
        filter,
        sort,
        page,
        SearchQueryBuilders::incidentProcessInstanceStatisticsByDefinitionQuery);
  }
}
