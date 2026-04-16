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
import io.camunda.gateway.mapping.http.search.contract.BatchOperationItemFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ClusterVariableSearchQueryFilterRequestMapper;
import io.camunda.gateway.mapping.http.search.contract.CorrelatedMessageSubscriptionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.DecisionDefinitionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.DecisionInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.DecisionRequirementsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ElementInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.GlobalJobStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.GlobalTaskListenerFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.GroupFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.IncidentFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.IncidentProcessInstanceStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobErrorStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobTimeSeriesStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobTypeStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.JobWorkerStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.MappingRuleFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.MessageSubscriptionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessDefinitionFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessDefinitionInstanceVersionStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessDefinitionStatisticsFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.RoleFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.TenantFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.UserFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.UserTaskAuditLogFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.UserTaskFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.UserTaskVariableFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.VariableFilterMapper;
import io.camunda.gateway.mapping.http.validator.RequestValidator;
import io.camunda.gateway.protocol.model.AuditLogSearchQueryRequest;
import io.camunda.gateway.protocol.model.AuthorizationSearchQuery;
import io.camunda.gateway.protocol.model.BatchOperationItemSearchQuery;
import io.camunda.gateway.protocol.model.BatchOperationSearchQuery;
import io.camunda.gateway.protocol.model.ClusterVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionSearchQuery;
import io.camunda.gateway.protocol.model.CursorForwardPagination;
import io.camunda.gateway.protocol.model.DecisionDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.DecisionInstanceSearchQuery;
import io.camunda.gateway.protocol.model.DecisionRequirementsSearchQuery;
import io.camunda.gateway.protocol.model.ElementInstanceSearchQuery;
import io.camunda.gateway.protocol.model.GlobalTaskListenerSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.GroupUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.JobSearchQuery;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQuery;
import io.camunda.gateway.protocol.model.JobTypeStatisticsQuery;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsQuery;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryRequest;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQuery;
import io.camunda.gateway.protocol.model.OffsetPagination;
import io.camunda.gateway.protocol.model.ProcessDefinitionElementStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuery;
import io.camunda.gateway.protocol.model.RoleClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleSearchQueryRequest;
import io.camunda.gateway.protocol.model.RoleUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.SearchQueryPageRequest;
import io.camunda.gateway.protocol.model.TenantClientSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantGroupSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantSearchQueryRequest;
import io.camunda.gateway.protocol.model.TenantUserSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserTaskAuditLogSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserTaskEffectiveVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserTaskSearchQuery;
import io.camunda.gateway.protocol.model.UserTaskVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.VariableSearchQuery;
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
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ProblemDetail;

public final class SearchQueryRequestMapper {

  private SearchQueryRequestMapper() {}

  private static OffsetDateTime toOffsetDateTime(final String text) {
    return StringUtils.isEmpty(text) ? null : OffsetDateTime.parse(text);
  }

  /**
   * Converts a list of generated sort DTOs into {@link SearchQuerySortRequest}s. All generated sort
   * DTOs follow the same shape ({@code getField().getValue()} and {@code getOrder().getValue()})
   * but share no common interface, so the accessors are passed as functions.
   */
  private static <S> List<SearchQuerySortRequest> toSortRequests(
      final List<S> sort,
      final Function<S, String> fieldExtractor,
      final Function<S, String> orderExtractor) {
    if (sort == null) {
      return List.of();
    }
    return sort.stream()
        .map(s -> new SearchQuerySortRequest(fieldExtractor.apply(s), orderExtractor.apply(s)))
        .toList();
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
      final UserTaskEffectiveVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);
    final var filter = UserTaskVariableFilterMapper.toUserTaskVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  /**
   * Overload that accepts flat page fields (no polymorphic subtypes). Determines pagination type
   * from which fields are non-null.
   */
  static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final SearchQueryPageRequest page) {
    if (page == null) {
      return Either.right(null);
    }
    return toSearchQueryPage(page.getLimit(), page.getFrom(), page.getAfter(), page.getBefore());
  }

  static Either<List<String>, SearchQueryPage> toSearchQueryPage(final OffsetPagination page) {
    if (page == null) {
      return Either.right(null);
    }
    return toSearchQueryPage(page.getLimit(), page.getFrom(), null, null);
  }

  static Either<List<String>, SearchQueryPage> toSearchQueryPage(
      final CursorForwardPagination page) {
    if (page == null) {
      return Either.right(null);
    }
    return toSearchQueryPage(page.getLimit(), null, page.getAfter(), null);
  }

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
          final long processDefinitionKey, final ProcessDefinitionElementStatisticsQuery request) {
    if (request == null) {
      return Either.right(
          new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey).build());
    }
    final var filter =
        ProcessDefinitionStatisticsFilterMapper.toProcessDefinitionStatisticsFilter(
            processDefinitionKey, request.getFilter());
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
      toJobTypeStatisticsQuery(final JobTypeStatisticsQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobTypeStatisticsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var filter = JobTypeStatisticsFilterMapper.toJobTypeStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTypeStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobWorkerStatisticsQuery>
      toJobWorkerStatisticsQuery(final JobWorkerStatisticsQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobWorkerStatisticsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var filter =
        JobWorkerStatisticsFilterMapper.toJobWorkerStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobWorkerStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, io.camunda.search.query.JobTimeSeriesStatisticsQuery>
      toJobTimeSeriesStatisticsQuery(final JobTimeSeriesStatisticsQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobTimeSeriesStatisticsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var filter =
        JobTimeSeriesStatisticsFilterMapper.toJobTimeSeriesStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobTimeSeriesStatisticsSearchQuery);
  }

  public static Either<ProblemDetail, JobErrorStatisticsQuery> toJobErrorStatisticsQuery(
      final io.camunda.gateway.protocol.model.JobErrorStatisticsQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobErrorStatisticsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var filter =
        JobErrorStatisticsFilterMapper.toJobErrorStatisticsFilter(request.getFilter());
    return buildSearchQuery(
        filter, Either.right(null), page, SearchQueryBuilders::jobErrorStatisticsSearchQuery);
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
    final var page = toSearchQueryPage(request.getPage());
    final var filter =
        MessageSubscriptionFilterMapper.toMessageSubscriptionFilter(request.getFilter());
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
          final ProcessDefinitionInstanceStatisticsQuery request) {
    final var filter =
        FilterBuilders.processInstance().states(ProcessInstanceState.ACTIVE.name()).build();
    if (request == null) {
      return Either.right(
          SearchQueryBuilders.processDefinitionInstanceStatisticsQuery().filter(filter).build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
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
          final ProcessDefinitionInstanceVersionStatisticsQuery request) {
    if (request == null || request.getFilter() == null) {
      final var problem =
          RequestValidator.createProblemDetail(
              List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
      if (problem.isPresent()) {
        return Either.left(problem.get());
      }
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::processDefinitionInstanceVersionStatistics,
            SearchQuerySortRequestMapper::applyProcessDefinitionInstanceVersionStatisticsSortField);
    final var filter =
        ProcessDefinitionInstanceVersionStatisticsFilterMapper
            .toProcessDefinitionInstanceVersionStatisticsFilter(request.getFilter());
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
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
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
          final IncidentProcessInstanceStatisticsByDefinitionQuery request) {
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::incidentProcessInstanceStatisticsByDefinition,
            SearchQuerySortRequestMapper
                ::applyIncidentProcessInstanceStatisticsByDefinitionSortField);
    final var filter =
        IncidentProcessInstanceStatisticsFilterMapper
            .toIncidentProcessInstanceStatisticsByDefinitionFilter(request.getFilter());
    return buildSearchQuery(
        filter,
        sort,
        page,
        SearchQueryBuilders::incidentProcessInstanceStatisticsByDefinitionQuery);
  }

  // -- Strict contract overloads (no protocol model dependency) --

  public static Either<ProblemDetail, AuditLogQuery> toAuditLogQueryStrict(
      final AuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = AuditLogFilterMapper.toAuditLogFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, AuthorizationQuery> toAuthorizationQueryStrict(
      final AuthorizationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.authorizationSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::authorization,
            SearchQuerySortRequestMapper::applyAuthorizationSortField);
    final var filter = AuthorizationFilterMapper.toAuthorizationFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::authorizationSearchQuery);
  }

  public static Either<ProblemDetail, BatchOperationItemQuery> toBatchOperationItemQueryStrict(
      final BatchOperationItemSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationItemQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::batchOperationItem,
            SearchQuerySortRequestMapper::applyBatchOperationItemSortField);
    final var filter =
        BatchOperationItemFilterMapper.toBatchOperationItemFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationItemQuery);
  }

  public static Either<ProblemDetail, BatchOperationQuery> toBatchOperationQueryStrict(
      final BatchOperationSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.batchOperationQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::batchOperation,
            SearchQuerySortRequestMapper::applyBatchOperationSortField);
    final var filter = BatchOperationFilterMapper.toBatchOperationFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::batchOperationQuery);
  }

  public static Either<ProblemDetail, ClusterVariableQuery> toClusterVariableQueryStrict(
      final ClusterVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.clusterVariableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::clusterVariable,
            SearchQuerySortRequestMapper::applyClusterVariableSortField);
    final var filter =
        ClusterVariableSearchQueryFilterRequestMapper.toClusterVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::clusterVariableSearchQuery);
  }

  public static Either<ProblemDetail, CorrelatedMessageSubscriptionQuery>
      toCorrelatedMessageSubscriptionQueryStrict(
          final CorrelatedMessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.correlatedMessageSubscriptionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::correlatedMessageSubscription,
            SearchQuerySortRequestMapper::applyCorrelatedMessageSubscriptionSortField);
    final var filter =
        CorrelatedMessageSubscriptionFilterMapper.toCorrelatedMessageSubscriptionFilter(
            request.getFilter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::correlatedMessageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionDefinitionQuery> toDecisionDefinitionQueryStrict(
      final DecisionDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionDefinitionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::decisionDefinition,
            SearchQuerySortRequestMapper::applyDecisionDefinitionSortField);
    final var filter =
        DecisionDefinitionFilterMapper.toDecisionDefinitionFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, DecisionInstanceQuery> toDecisionInstanceQueryStrict(
      final DecisionInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::decisionInstance,
            SearchQuerySortRequestMapper::applyDecisionInstanceSortField);
    final var filter = DecisionInstanceFilterMapper.toDecisionInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::decisionInstanceSearchQuery);
  }

  public static Either<ProblemDetail, DecisionRequirementsQuery> toDecisionRequirementsQueryStrict(
      final DecisionRequirementsSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.decisionRequirementsSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::decisionRequirements,
            SearchQuerySortRequestMapper::applyDecisionRequirementsSortField);
    final var filter =
        DecisionRequirementsFilterMapper.toDecisionRequirementsFilter(request.getFilter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::decisionRequirementsSearchQuery);
  }

  public static Either<ProblemDetail, FlowNodeInstanceQuery> toElementInstanceQueryStrict(
      final ElementInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.flownodeInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::flowNodeInstance,
            SearchQuerySortRequestMapper::applyElementInstanceSortField);
    final var filter = ElementInstanceFilterMapper.toElementInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::flownodeInstanceSearchQuery);
  }

  public static Either<ProblemDetail, GlobalListenerQuery> toGlobalTaskListenerQueryStrict(
      final GlobalTaskListenerSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.globalListenerSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::globalListener,
            SearchQuerySortRequestMapper::applyGlobalTaskListenerSortField);
    final var filter =
        GlobalTaskListenerFilterMapper.toGlobalTaskListenerFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::globalListenerSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupClientQueryStrict(
      final GroupClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupClientSortField);
    final var filter = FilterBuilders.groupMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, GroupQuery> toGroupQueryStrict(
      final GroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::group,
            SearchQuerySortRequestMapper::applyGroupSortField);
    final var filter = GroupFilterMapper.toGroupFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupSearchQuery);
  }

  public static Either<ProblemDetail, GroupMemberQuery> toGroupUserQueryStrict(
      final GroupUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.groupMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::groupMember,
            SearchQuerySortRequestMapper::applyGroupUserSortField);
    final var filter = FilterBuilders.groupMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::groupMemberSearchQuery);
  }

  public static Either<ProblemDetail, IncidentQuery> toIncidentQueryStrict(
      final IncidentSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.incidentSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::incident,
            SearchQuerySortRequestMapper::applyIncidentSortField);
    final var filter = IncidentFilterMapper.toIncidentFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::incidentSearchQuery);
  }

  public static Either<ProblemDetail, JobQuery> toJobQueryStrict(final JobSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.jobSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests, SortOptionBuilders::job, SearchQuerySortRequestMapper::applyJobSortField);
    final var filter = JobFilterMapper.toJobFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::jobSearchQuery);
  }

  public static Either<ProblemDetail, MappingRuleQuery> toMappingRuleQueryStrict(
      final MappingRuleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.mappingRuleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::mappingRule,
            SearchQuerySortRequestMapper::applyMappingRuleSortField);
    final var filter = MappingRuleFilterMapper.toMappingRuleFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::mappingRuleSearchQuery);
  }

  public static Either<ProblemDetail, MessageSubscriptionQuery> toMessageSubscriptionQueryStrict(
      final MessageSubscriptionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.messageSubscriptionSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::messageSubscription,
            SearchQuerySortRequestMapper::applyMessageSubscriptionSortField);
    final var filter =
        MessageSubscriptionFilterMapper.toMessageSubscriptionFilter(request.getFilter());
    return buildSearchQuery(
        filter, sort, page, SearchQueryBuilders::messageSubscriptionSearchQuery);
  }

  public static Either<ProblemDetail, ProcessDefinitionQuery> toProcessDefinitionQueryStrict(
      final ProcessDefinitionSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processDefinitionSearchQuery().build());
    }

    // Validate isLatestVersion constraints before processing
    final var isLatestVersionValidation = validateIsLatestVersionConstraintsStrict(request);
    if (isLatestVersionValidation.isLeft()) {
      return Either.left(isLatestVersionValidation.getLeft());
    }

    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::processDefinition,
            SearchQuerySortRequestMapper::applyProcessDefinitionSortField);
    final var filter = ProcessDefinitionFilterMapper.toProcessDefinitionFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processDefinitionSearchQuery);
  }

  public static Either<ProblemDetail, ProcessInstanceQuery> toProcessInstanceQueryStrict(
      final ProcessInstanceSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.processInstanceSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::processInstance,
            SearchQuerySortRequestMapper::applyProcessInstanceSortField);
    final var filter = ProcessInstanceFilterMapper.toProcessInstanceFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::processInstanceSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleClientQueryStrict(
      final RoleClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleClientSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleGroupQueryStrict(
      final RoleGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleGroupSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, RoleQuery> toRoleQueryStrict(
      final RoleSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::role,
            SearchQuerySortRequestMapper::applyRoleSortField);
    final var filter = RoleFilterMapper.toRoleFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleSearchQuery);
  }

  public static Either<ProblemDetail, RoleMemberQuery> toRoleUserQueryStrict(
      final RoleUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.roleMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::roleMember,
            SearchQuerySortRequestMapper::applyRoleUserSortField);
    final var filter = FilterBuilders.roleMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::roleMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantClientQueryStrict(
      final TenantClientSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantClientSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantGroupQueryStrict(
      final TenantGroupSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantGroupSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, TenantQuery> toTenantQueryStrict(
      final TenantSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenant,
            SearchQuerySortRequestMapper::applyTenantSortField);
    final var filter = TenantFilterMapper.toTenantFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantSearchQuery);
  }

  public static Either<ProblemDetail, TenantMemberQuery> toTenantUserQueryStrict(
      final TenantUserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.tenantMemberSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::tenantMember,
            SearchQuerySortRequestMapper::applyTenantUserSortField);
    final var filter = FilterBuilders.tenantMember().build();
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::tenantMemberSearchQuery);
  }

  public static Either<ProblemDetail, UserQuery> toUserQueryStrict(
      final UserSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::user,
            SearchQuerySortRequestMapper::applyUserSortField);
    final var filter = UserFilterMapper.toUserFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userSearchQuery);
  }

  public static Either<ProblemDetail, AuditLogQuery> toUserTaskAuditLogQueryStrict(
      final UserTaskAuditLogSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.auditLogSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests = java.util.List.<SearchQuerySortRequest>of();
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::auditLog,
            SearchQuerySortRequestMapper::applyAuditLogSortField);
    final var filter = UserTaskAuditLogFilterMapper.toUserTaskAuditLogFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::auditLogSearchQuery);
  }

  public static Either<ProblemDetail, UserTaskQuery> toUserTaskQueryStrict(
      final UserTaskSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.userTaskSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::userTask,
            SearchQuerySortRequestMapper::applyUserTaskSortField);
    final var filter = UserTaskFilterMapper.toUserTaskFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::userTaskSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toUserTaskVariableQueryStrict(
      final UserTaskVariableSearchQueryRequest request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyUserTaskVariableSortField);
    final var filter = UserTaskVariableFilterMapper.toUserTaskVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  public static Either<ProblemDetail, VariableQuery> toVariableQueryStrict(
      final VariableSearchQuery request) {
    if (request == null) {
      return Either.right(SearchQueryBuilders.variableSearchQuery().build());
    }
    final var page = toSearchQueryPage(request.getPage());
    final var sortRequests =
        toSortRequests(
            request.getSort(),
            s -> s.getField().getValue(),
            s -> s.getOrder() != null ? s.getOrder().getValue() : null);
    final var sort =
        SearchQuerySortRequestMapper.toSearchQuerySort(
            sortRequests,
            SortOptionBuilders::variable,
            SearchQuerySortRequestMapper::applyVariableSortField);
    final var filter = VariableFilterMapper.toVariableFilter(request.getFilter());
    return buildSearchQuery(filter, sort, page, SearchQueryBuilders::variableSearchQuery);
  }

  private static Either<ProblemDetail, Void> validateIsLatestVersionConstraintsStrict(
      final ProcessDefinitionSearchQuery request) {
    final var filter = request.getFilter();
    if (filter == null || filter.getIsLatestVersion() == null || !filter.getIsLatestVersion()) {
      return Either.right(null);
    }

    final java.util.List<String> violations = new java.util.ArrayList<>();

    final var page = request.getPage();
    if (page != null) {
      if (page.getBefore() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION
                .formatted("before"));
      }
      if (page.getFrom() != null) {
        violations.add(
            io.camunda.gateway.mapping.http.validator.ErrorMessages
                .ERROR_MESSAGE_UNSUPPORTED_PAGINATION_WITH_IS_LATEST_VERSION
                .formatted("from"));
      }
    }

    final var sort = request.getSort();
    if (sort != null && !sort.isEmpty()) {
      final var allowedFields = java.util.Set.of("processDefinitionId", "tenantId");
      for (final var sortRequest : sort) {
        final var field = sortRequest.getField();
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
