/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.RequestMapper.getResult;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.*;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;

import io.camunda.gateway.mapping.http.search.contract.AuditLogFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.AuthorizationFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.BatchOperationFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.DecisionDefinitionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.DecisionInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.DecisionRequirementsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ElementInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.GlobalJobStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.GlobalTaskListenerFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.IncidentFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.IncidentProcessInstanceStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobErrorStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobTimeSeriesStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobTypeStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobWorkerStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessDefinitionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessDefinitionInstanceVersionStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessDefinitionStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.UserTaskAuditLogFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.UserTaskFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.UserTaskVariableFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.VariableFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationItemFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationItemSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ClusterVariableSearchQueryFilterRequestMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.ClusterVariableSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.CorrelatedMessageSubscriptionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.CorrelatedMessageSubscriptionSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DecisionDefinitionSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DecisionInstanceSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DecisionRequirementsSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ElementInstanceSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GlobalTaskListenerSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupClientSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupUserSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByDefinitionQuerySearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByErrorQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobErrorStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.JobSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobTimeSeriesStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobTypeStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobWorkerStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MappingRuleFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.MappingRuleSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MessageSubscriptionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.MessageSubscriptionSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionElementStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionInstanceStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionInstanceVersionStatisticsQuerySearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionMessageSubscriptionStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleClientSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleGroupSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleUserSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantClientSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantGroupSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantUserSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.UserSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserTaskAuditLogSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserTaskEffectiveVariableSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserTaskSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserTaskVariableSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.VariableSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.validator.RequestValidator;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
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
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ProblemDetail;

public final class SearchQueryRequestMapper {

  private SearchQueryRequestMapper() {}

  private static OffsetDateTime toOffsetDateTime(final String text) {
    return StringUtils.isEmpty(text) ? null : OffsetDateTime.parse(text);
  }

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
                        .startTime(toOffsetDateTime(startTime))
                        .endTime(toOffsetDateTime(endTime))
                        .tenantId(tenantId)
                        .withTenants(withTenants)
                        .build())
                .build());
  }

  public static Either<ProblemDetail, io.camunda.search.query.GlobalJobStatisticsQuery>
      toGlobalJobStatisticsQuery(
          final OffsetDateTime from, final OffsetDateTime to, final String jobType) {
    final var filter =
        GlobalJobStatisticsFilterMapper.toGlobalJobStatisticsFilter(from, to, jobType);

    if (filter.isLeft()) {
      final var problem = RequestValidator.createProblemDetail(filter.getLeft());
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    return Either.right(
        SearchQueryBuilders.globalJobStatisticsSearchQuery().filter(filter.get()).build());
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskEffectiveVariableQueryStrict(
      final UserTaskEffectiveVariableSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), null, null)
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);
    final var filter = UserTaskVariableFilterMapper.toUserTaskVariableFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
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
          final ProcessDefinitionElementStatisticsQueryContract request) {
    if (request == null) {
      return Either.right(
          new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey).build());
    }
    final var filter =
        ProcessDefinitionStatisticsFilterMapper.toProcessDefinitionStatisticsFilter(
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
      toJobTypeStatisticsQuery(final JobTypeStatisticsQueryContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobTypeStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter = JobTypeStatisticsFilterMapper.toJobTypeStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTypeStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobWorkerStatisticsQuery>
      toJobWorkerStatisticsQuery(final JobWorkerStatisticsQueryContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobWorkerStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter =
        JobWorkerStatisticsFilterMapper.toJobWorkerStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobWorkerStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobTimeSeriesStatisticsQuery>
      toJobTimeSeriesStatisticsQuery(final JobTimeSeriesStatisticsQueryContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobTimeSeriesStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter =
        JobTimeSeriesStatisticsFilterMapper.toJobTimeSeriesStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTimeSeriesStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, JobErrorStatisticsQuery> toJobErrorStatisticsQuery(
      final JobErrorStatisticsQueryContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobErrorStatisticsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter = JobErrorStatisticsFilterMapper.toJobErrorStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobErrorStatisticsSearchQuery);
  }

  public static Either<
          ProblemDetail,
          io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery>
      toProcessDefinitionMessageSubscriptionStatisticsQuery(
          final ProcessDefinitionMessageSubscriptionStatisticsQueryContract request) {
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.processDefinitionMessageSubscriptionStatisticsQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), null, p.after(), null)
            : Either.<List<String>, SearchQueryPage>right(null);
    final var filter =
        MessageSubscriptionFilterMapper.toMessageSubscriptionFilter(request.filter());
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
          final ProcessDefinitionInstanceStatisticsQueryContract request) {
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
          final ProcessDefinitionInstanceVersionStatisticsQuerySearchQueryRequestContract request) {
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
        ProcessDefinitionInstanceVersionStatisticsFilterMapper
            .toProcessDefinitionInstanceVersionStatisticsFilter(request.filter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::processDefinitionInstanceVersionStatisticsQuery);
  }

  public static Either<
          ProblemDetail, io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery>
      toIncidentProcessInstanceStatisticsByErrorQuery(
          final IncidentProcessInstanceStatisticsByErrorQueryContract request) {
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
          final IncidentProcessInstanceStatisticsByDefinitionQuerySearchQueryRequestContract
              request) {
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
        IncidentProcessInstanceStatisticsFilterMapper
            .toIncidentProcessInstanceStatisticsByDefinitionFilter(request.filter());
    return buildSearchQuery(
        filter,
        sort,
        page,
        SearchQueryBuilders::incidentProcessInstanceStatisticsByDefinitionQuery);
  }

  // -- Strict contract overloads (no protocol model dependency) --

  public static Either<ProblemDetail, AuditLogQuery> toAuditLogQueryStrict(
      final AuditLogSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = AuditLogFilterMapper.toAuditLogFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQueryStrict(
      final AuthorizationSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.authorizationSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::authorization,
            SearchQuerySortRequestMapper::applyAuthorizationSortField);
    final var filter = AuthorizationFilterMapper.toAuthorizationFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::authorizationSearchQuery);
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQueryStrict(
      final BatchOperationItemSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationItemQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::batchOperationItem,
            SearchQuerySortRequestMapper::applyBatchOperationItemSortField);
    final var filter = BatchOperationItemFilterMapper.toBatchOperationItemFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationItemQuery);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQueryStrict(
      final BatchOperationSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::batchOperation,
            SearchQuerySortRequestMapper::applyBatchOperationSortField);
    final var filter = BatchOperationFilterMapper.toBatchOperationFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationQuery);
  }

  public static Either<ProblemDetail, ClusterVariableQuery> toClusterVariableQueryStrict(
      final ClusterVariableSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.clusterVariableSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::clusterVariable,
            SearchQuerySortRequestMapper::applyClusterVariableSortField);
    final var filter =
        ClusterVariableSearchQueryFilterRequestMapper.toClusterVariableFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::clusterVariableSearchQuery);
  }

  public static Either<ProblemDetail, CorrelatedMessageSubscriptionQuery>
      toCorrelatedMessageSubscriptionQueryStrict(
          final CorrelatedMessageSubscriptionSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.correlatedMessageSubscriptionSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::correlatedMessageSubscription,
            SearchQuerySortRequestMapper::applyCorrelatedMessageSubscriptionSortField);
    final var filter =
        CorrelatedMessageSubscriptionFilterMapper.toCorrelatedMessageSubscriptionFilter(
            request.filter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::correlatedMessageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQueryStrict(
      final DecisionDefinitionSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::decisionDefinition,
            SearchQuerySortRequestMapper::applyDecisionDefinitionSortField);
    final var filter = DecisionDefinitionFilterMapper.toDecisionDefinitionFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQueryStrict(
      final DecisionInstanceSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::decisionInstance,
            SearchQuerySortRequestMapper::applyDecisionInstanceSortField);
    final var filter = DecisionInstanceFilterMapper.toDecisionInstanceFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQueryStrict(
      final DecisionRequirementsSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::decisionRequirements,
            SearchQuerySortRequestMapper::applyDecisionRequirementsSortField);
    final var filter =
        DecisionRequirementsFilterMapper.toDecisionRequirementsFilter(request.filter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionRequirementsSearchQuery);
  }

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toElementInstanceQueryStrict(
      final ElementInstanceSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::flowNodeInstance,
            SearchQuerySortRequestMapper::applyElementInstanceSortField);
    final var filter = ElementInstanceFilterMapper.toElementInstanceFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, GlobalListenerQuery> toGlobalTaskListenerQueryStrict(
      final GlobalTaskListenerSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.globalListenerSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::globalListener,
            SearchQuerySortRequestMapper::applyGlobalTaskListenerSortField);
    final var filter = GlobalTaskListenerFilterMapper.toGlobalTaskListenerFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::globalListenerSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupClientQueryStrict(
      final GroupClientSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupClientSortField);
    final var filter = FilterBuilders.groupMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQueryStrict(
      final GroupSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::group,
            SearchQuerySortRequestMapper::applyGroupSortField);
    final var filter = GroupFilterMapper.toGroupFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupUserQueryStrict(
      final GroupUserSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupUserSortField);
    final var filter = FilterBuilders.groupMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQueryStrict(
      final IncidentSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::incident,
            SearchQuerySortRequestMapper::applyIncidentSortField);
    final var filter = IncidentFilterMapper.toIncidentFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  public static Either<ProblemDetail, JobQuery> toJobQueryStrict(
      final JobSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            sortRequests, SortOptionBuilders::job, SearchQuerySortRequestMapper::applyJobSortField);
    final var filter = JobFilterMapper.toJobFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::jobSearchQuery);
  }

  public static Either<ProblemDetail, MappingRuleQuery> toMappingRuleQueryStrict(
      final MappingRuleSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.mappingRuleSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::mappingRule,
            SearchQuerySortRequestMapper::applyMappingRuleSortField);
    final var filter = MappingRuleFilterMapper.toMappingRuleFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::mappingRuleSearchQuery);
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQueryStrict(
      final MessageSubscriptionSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.messageSubscriptionSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::messageSubscription,
            SearchQuerySortRequestMapper::applyMessageSubscriptionSortField);
    final var filter =
        MessageSubscriptionFilterMapper.toMessageSubscriptionFilter(request.filter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::messageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQueryStrict(
      final ProcessDefinitionSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }

    // Validate isLatestVersion constraints before processing
    final var isLatestVersionValidation = validateIsLatestVersionConstraintsStrict(request);
    if (isLatestVersionValidation.isLeft()) {
      return Either.left(isLatestVersionValidation.getLeft());
    }

    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::processDefinition,
            SearchQuerySortRequestMapper::applyProcessDefinitionSortField);
    final var filter = ProcessDefinitionFilterMapper.toProcessDefinitionFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQueryStrict(
      final ProcessInstanceSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::processInstance,
            SearchQuerySortRequestMapper::applyProcessInstanceSortField);
    final var filter = ProcessInstanceFilterMapper.toProcessInstanceFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleClientQueryStrict(
      final RoleClientSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleClientSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleGroupQueryStrict(
      final RoleGroupSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleGroupSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQueryStrict(
      final RoleSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::role,
            SearchQuerySortRequestMapper::applyRoleSortField);
    final var filter = RoleFilterMapper.toRoleFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleUserQueryStrict(
      final RoleUserSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleUserSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantClientQueryStrict(
      final TenantClientSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantClientSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantGroupQueryStrict(
      final TenantGroupSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantGroupSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQueryStrict(
      final TenantSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::tenant,
            SearchQuerySortRequestMapper::applyTenantSortField);
    final var filter = TenantFilterMapper.toTenantFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantUserQueryStrict(
      final TenantUserSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantUserSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, UserQuery> toUserQueryStrict(
      final UserSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::user,
            SearchQuerySortRequestMapper::applyUserSortField);
    final var filter = UserFilterMapper.toUserFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toUserTaskAuditLogQueryStrict(
      final UserTaskAuditLogSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
    final var sortRequests = java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = UserTaskAuditLogFilterMapper.toUserTaskAuditLogFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQueryStrict(
      final UserTaskSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::userTask,
            SearchQuerySortRequestMapper::applyUserTaskSortField);
    final var filter = UserTaskFilterMapper.toUserTaskFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQueryStrict(
      final UserTaskVariableSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);
    final var filter = UserTaskVariableFilterMapper.toUserTaskVariableFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQueryStrict(
      final VariableSearchQueryRequestContract request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var p = request.page();
    final var page =
        p != null
            ? toSearchQueryPage(p.limit(), p.from(), p.after(), p.before())
            : toSearchQueryPage(null, null, null, null);
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
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyVariableSortField);
    final var filter = VariableFilterMapper.toVariableFilter(request.filter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  private static Either<ProblemDetail, Void> validateIsLatestVersionConstraintsStrict(
      final ProcessDefinitionSearchQueryRequestContract request) {
    final var filter = request.filter();
    if (filter == null || filter.isLatestVersion() == null || !filter.isLatestVersion()) {
      return Either.right(null);
    }

    final java.util.List<String> violations = new java.util.ArrayList<>();

    final var page = request.page();
    if (page != null) {
      if (page.before() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION
                .formatted("before"));
      }
      if (page.from() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION
                .formatted("from"));
      }
    }

    final var sort = request.sort();
    if (sort != null && !sort.isEmpty()) {
      final var allowedFields = java.util.Set.of("processDefinitionId", "tenantId");
      for (final var sortRequest : sort) {
        final var field = sortRequest.field();
        if (field != null && !allowedFields.contains(field.getValue())) {
          violations.add(
              io.camunda.gateway.mapping.http.validator.ErrorMessages
                  .ERROR_MESSAGE_UNSUPPORTED_SORT_FIELD_WITH_IS_LATEST_VERSION
                  .formatted(field.getValue()));
        }
      }
    }

    if (!violations.isEmpty()) {
      final var problem =
          io.camunda.gateway.mapping.http.validator.RequestValidator.createProblemDetail(
              violations);
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }

    return Either.right(null);
  }
}
