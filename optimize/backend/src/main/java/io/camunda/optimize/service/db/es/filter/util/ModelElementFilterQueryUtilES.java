/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.util;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES.getDurationFilterScript;
import static io.camunda.optimize.service.db.report.filter.util.ModelElementFilterQueryUtil.getViewLevelFiltersForInstanceMatch;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TENANT_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_MI_BODY;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FlowNodeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.function.TriFunction;

public final class ModelElementFilterQueryUtilES {

  private static final Map<
          Class<? extends ProcessFilterDto<?>>, Function<BoolQuery.Builder, BoolQuery.Builder>>
      FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES =
          Map.of(
              RunningFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilES::createRunningFlowNodesOnlyFilterQuery,
              CompletedFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilES::createCompletedFlowNodesOnlyFilterQuery,
              CompletedOrCanceledFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilES::createCompletedOrCanceledFlowNodesOnlyFilterQuery,
              CanceledFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilES::createCanceledFlowNodesOnlyFilterQuery);

  private static final Map<
          Class<? extends ProcessFilterDto<?>>,
          TriFunction<FlowNodeDateFilterDataDto<?>, ZoneId, Builder, Builder>>
      FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES =
          Map.of(
              FlowNodeStartDateFilterDto.class,
              ModelElementFilterQueryUtilES::createFlowNodeStartDateFilterQuery,
              FlowNodeEndDateFilterDto.class,
              ModelElementFilterQueryUtilES::createFlowNodeEndDateFilterQuery);

  private static final NestedDefinitionQueryBuilderES NESTED_DEFINITION_QUERY_BUILDER =
      new NestedDefinitionQueryBuilderES(
          FLOW_NODE_INSTANCES,
          FLOW_NODE_DEFINITION_KEY,
          FLOW_NODE_DEFINITION_VERSION,
          FLOW_NODE_TENANT_ID);

  private ModelElementFilterQueryUtilES() {}

  public static Optional<NestedQuery.Builder> addInstanceFilterForRelevantViewLevelFilters(
      final List<ProcessFilterDto<?>> filters, final FilterContext filterContext) {
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch =
        getViewLevelFiltersForInstanceMatch(filters);
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      final BoolQuery.Builder viewFilterInstanceQuery =
          createFlowNodeTypeFilterQuery(filterContext.isUserTaskReport());
      viewLevelFiltersForInstanceMatch.forEach(
          filter -> {
            if (filter instanceof FlowNodeDurationFilterDto) {
              final FlowNodeDurationFiltersDataDto filterData =
                  (FlowNodeDurationFiltersDataDto) filter.getData();
              createFlowNodeDurationFilterQuery(filterData, viewFilterInstanceQuery);
            } else if (filter instanceof CandidateGroupFilterDto) {
              final IdentityLinkFilterDataDto filterData =
                  (IdentityLinkFilterDataDto) filter.getData();
              createCandidateGroupFilterQuery(filterData, viewFilterInstanceQuery);
            } else if (filter instanceof AssigneeFilterDto) {
              final IdentityLinkFilterDataDto filterData =
                  (IdentityLinkFilterDataDto) filter.getData();
              createAssigneeFilterQuery(filterData, viewFilterInstanceQuery);
            } else if (filter instanceof ExecutedFlowNodeFilterDto) {
              final ExecutedFlowNodeFilterDataDto filterData =
                  (ExecutedFlowNodeFilterDataDto) filter.getData();
              createExecutedFlowNodeFilterQuery(filterData, viewFilterInstanceQuery);
            } else if (FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass())) {
              FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES
                  .get(filter.getClass())
                  .apply(
                      (FlowNodeDateFilterDataDto<?>) filter.getData(),
                      filterContext.getTimezone(),
                      viewFilterInstanceQuery);
            } else {
              FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES
                  .get(filter.getClass())
                  .apply(viewFilterInstanceQuery);
            }
          });
      final NestedQuery.Builder builder = new NestedQuery.Builder();
      builder
          .path(FLOW_NODE_INSTANCES)
          .scoreMode(ChildScoreMode.None)
          .query(q -> q.bool(viewFilterInstanceQuery.build()));
      return Optional.of(builder);
    }
    return Optional.empty();
  }

  public static BoolQuery.Builder createModelElementAggregationFilter(
      final ProcessReportDataDto reportData,
      final FilterContext filterContext,
      final DefinitionService definitionService) {
    final BoolQuery.Builder filterBoolQuery =
        createFlowNodeTypeFilterQuery(reportData).minimumShouldMatch("1");
    final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition =
        reportData.groupFiltersByDefinitionIdentifier();
    reportData
        .getDefinitions()
        .forEach(
            definitionDto -> {
              final BoolQuery.Builder flowNodeDefinitionQuery = new BoolQuery.Builder();
              flowNodeDefinitionQuery.must(
                  m ->
                      NESTED_DEFINITION_QUERY_BUILDER.createNestedDocDefinitionQuery(
                          definitionDto.getKey(),
                          definitionDto.getVersions(),
                          definitionDto.getTenantIds(),
                          definitionService));
              addModelElementFilters(
                  flowNodeDefinitionQuery,
                  filterContext,
                  filtersByDefinition.getOrDefault(
                      definitionDto.getIdentifier(), Collections.emptyList()));
              filterBoolQuery.should(s -> s.bool(flowNodeDefinitionQuery.build()));
            });

    addModelElementFilters(
        filterBoolQuery,
        filterContext,
        filtersByDefinition.getOrDefault(APPLIED_TO_ALL_DEFINITIONS, Collections.emptyList()));
    return filterBoolQuery;
  }

  private static void addModelElementFilters(
      final BoolQuery.Builder filterBoolQuery,
      final FilterContext filterContext,
      final List<ProcessFilterDto<?>> filters) {
    if (filters.isEmpty()) {
      return;
    }
    addFlowNodeStatusFilter(filterBoolQuery, filters);
    addFlowNodeDurationFilter(filterBoolQuery, filters);
    addFlowNodeIdFilter(filterBoolQuery, filters);
    addFlowNodeStartDateFilter(filterBoolQuery, filterContext.getTimezone(), filters);
    addFlowNodeEndDateFilter(filterBoolQuery, filterContext.getTimezone(), filters);
    addAssigneeFilter(filterBoolQuery, filters);
    addCandidateGroupFilter(filterBoolQuery, filters);
  }

  public static BoolQuery.Builder createUserTaskFlowNodeTypeFilter() {
    return new BoolQuery.Builder()
        .must(
            m ->
                m.term(
                    t ->
                        t.field(nestedFieldReference(FLOW_NODE_TYPE))
                            .value(FLOW_NODE_TYPE_USER_TASK)));
  }

  public static BoolQuery.Builder createInclusiveFlowNodeIdFilterQuery(
      final ProcessReportDataDto reportDataDto,
      final Set<String> flowNodeIds,
      final FilterContext filterContext,
      final DefinitionService definitionService) {
    return createExecutedFlowNodeFilterQuery(
        createModelElementAggregationFilter(reportDataDto, filterContext, definitionService),
        nestedFieldReference(FLOW_NODE_ID),
        new ArrayList<>(flowNodeIds),
        IN);
  }

  public static BoolQuery.Builder createExecutedFlowNodeFilterQuery(
      final ExecutedFlowNodeFilterDataDto executedFlowNodeFilterData,
      final BoolQuery.Builder boolQuery) {
    return createExecutedFlowNodeFilterQuery(
        boolQuery,
        nestedFieldReference(FLOW_NODE_ID),
        executedFlowNodeFilterData.getValues(),
        executedFlowNodeFilterData.getOperator());
  }

  public static BoolQuery.Builder createExecutedFlowNodeFilterQuery(
      final ExecutedFlowNodeFilterDataDto executedFlowNodeFilterData,
      final String nestedFieldReference,
      final BoolQuery.Builder boolQuery) {
    return createExecutedFlowNodeFilterQuery(
        boolQuery,
        nestedFieldReference,
        executedFlowNodeFilterData.getValues(),
        executedFlowNodeFilterData.getOperator());
  }

  public static BoolQuery.Builder createExecutedFlowNodeFilterQuery(
      final BoolQuery.Builder boolQuery,
      final String nestedFieldReference,
      final List<String> flowNodeIds,
      final MembershipFilterOperator operator) {
    final TermsQuery.Builder builder = new TermsQuery.Builder();
    builder.field(nestedFieldReference);
    builder.terms(tt -> tt.value(flowNodeIds.stream().map(FieldValue::of).toList()));
    if (IN.equals(operator)) {
      boolQuery.filter(f -> f.terms(builder.build()));
    } else {
      boolQuery.filter(f -> f.bool(b -> b.mustNot(m -> m.terms(builder.build()))));
    }
    return boolQuery;
  }

  public static BoolQuery.Builder createFlowNodeDurationFilterQuery(
      final FlowNodeDurationFiltersDataDto durationFilterData) {
    return createFlowNodeDurationFilterQuery(durationFilterData, new BoolQuery.Builder());
  }

  public static BoolQuery.Builder createRunningFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder boolQuery) {
    boolQuery
        .mustNot(
            m ->
                m.term(
                    t ->
                        t.field(nestedFieldReference(FLOW_NODE_TYPE))
                            .value(FLOW_NODE_TYPE_MI_BODY)))
        .mustNot(m -> m.exists(e -> e.field(nestedFieldReference(FLOW_NODE_END_DATE))));
    return boolQuery;
  }

  public static BoolQuery.Builder createCompletedFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder boolQuery) {
    boolQuery
        .mustNot(
            m ->
                m.term(
                    t ->
                        t.field(nestedFieldReference(FLOW_NODE_TYPE))
                            .value(FLOW_NODE_TYPE_MI_BODY)))
        .must(m -> m.term(t -> t.field(nestedFieldReference(FLOW_NODE_CANCELED)).value(false)))
        .must(m -> m.exists(t -> t.field(nestedFieldReference(FLOW_NODE_END_DATE))));
    return boolQuery;
  }

  public static BoolQuery.Builder createCanceledFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder boolQuery) {
    boolQuery
        .mustNot(
            m ->
                m.term(
                    t ->
                        t.field(nestedFieldReference(FLOW_NODE_TYPE))
                            .value(FLOW_NODE_TYPE_MI_BODY)))
        .must(m -> m.term(t -> t.field(nestedFieldReference(FLOW_NODE_CANCELED)).value(true)));
    return boolQuery;
  }

  public static BoolQuery.Builder createCompletedOrCanceledFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder builder) {
    builder
        .mustNot(
            m ->
                m.term(
                    t ->
                        t.field(nestedFieldReference(FLOW_NODE_TYPE))
                            .value(FLOW_NODE_TYPE_MI_BODY)))
        .must(m -> m.exists(e -> e.field(nestedFieldReference(FLOW_NODE_END_DATE))));
    return builder;
  }

  public static BoolQuery.Builder createFlowNodeStartDateFilterQuery(
      final FlowNodeDateFilterDataDto<?> filterData, final ZoneId timezone) {
    return createFlowNodeStartDateFilterQuery(filterData, timezone, new BoolQuery.Builder());
  }

  public static BoolQuery.Builder createFlowNodeStartDateFilterQuery(
      final FlowNodeDateFilterDataDto<?> filterData,
      final ZoneId timezone,
      final BoolQuery.Builder queryBuilder) {
    return createFlowNodeDateFilterQuery(
        filterData, timezone, queryBuilder, nestedFieldReference(FLOW_NODE_START_DATE));
  }

  public static BoolQuery.Builder createFlowNodeEndDateFilterQuery(
      final FlowNodeDateFilterDataDto<?> filterData, final ZoneId timezone) {
    return createFlowNodeEndDateFilterQuery(filterData, timezone, new BoolQuery.Builder());
  }

  public static BoolQuery.Builder createFlowNodeEndDateFilterQuery(
      final FlowNodeDateFilterDataDto<?> filterData,
      final ZoneId timezone,
      final BoolQuery.Builder queryBuilder) {
    return createFlowNodeDateFilterQuery(
        filterData, timezone, queryBuilder, nestedFieldReference(FLOW_NODE_END_DATE));
  }

  private static BoolQuery.Builder createFlowNodeDateFilterQuery(
      final FlowNodeDateFilterDataDto<?> filterData,
      final ZoneId timezone,
      final BoolQuery.Builder queryBuilder,
      final String nestedDateField) {
    final Optional<RangeQuery> rangeQuery =
        DateFilterQueryUtilES.createRangeQuery(filterData, nestedDateField, timezone);
    rangeQuery.ifPresent(
        dateRangeQuery -> {
          if (CollectionUtils.isEmpty(filterData.getFlowNodeIds())) {
            queryBuilder.filter(f -> f.range(dateRangeQuery));
          } else {
            queryBuilder.minimumShouldMatch("1");
            filterData
                .getFlowNodeIds()
                .forEach(
                    flowNodeId ->
                        queryBuilder.should(
                            s ->
                                s.bool(
                                    b ->
                                        b.must(
                                                m ->
                                                    m.terms(
                                                        t ->
                                                            t.field(
                                                                    nestedFieldReference(
                                                                        FLOW_NODE_ID))
                                                                .terms(
                                                                    tt ->
                                                                        tt.value(
                                                                            List.of(
                                                                                FieldValue.of(
                                                                                    flowNodeId))))))
                                            .must(m -> m.range(dateRangeQuery)))));
          }
        });
    return queryBuilder;
  }

  public static BoolQuery.Builder createAssigneeFilterQuery(
      final IdentityLinkFilterDataDto assigneeFilter) {
    return createAssigneeFilterQuery(assigneeFilter, new BoolQuery.Builder());
  }

  private static BoolQuery.Builder createAssigneeFilterQuery(
      final IdentityLinkFilterDataDto assigneeFilter, final BoolQuery.Builder queryBuilder) {
    return createIdentityLinkFilterQuery(assigneeFilter, USER_TASK_ASSIGNEE, queryBuilder);
  }

  public static BoolQuery.Builder createCandidateGroupFilterQuery(
      final IdentityLinkFilterDataDto candidateGroupFilter) {
    return createCandidateGroupFilterQuery(candidateGroupFilter, new BoolQuery.Builder());
  }

  private static BoolQuery.Builder createCandidateGroupFilterQuery(
      final IdentityLinkFilterDataDto candidateGroupFilter, final BoolQuery.Builder queryBuilder) {
    return createIdentityLinkFilterQuery(
        candidateGroupFilter, USER_TASK_CANDIDATE_GROUPS, queryBuilder);
  }

  private static BoolQuery.Builder createIdentityLinkFilterQuery(
      final IdentityLinkFilterDataDto identityFilter,
      final String valueField,
      final BoolQuery.Builder queryBuilder) {
    if (CollectionUtils.isEmpty(identityFilter.getValues())) {
      throw new OptimizeValidationException("Filter values are not allowed to be empty.");
    }

    final AtomicBoolean includeNull = new AtomicBoolean(false);
    final Set<String> nonNullValues =
        identityFilter.getValues().stream()
            .peek(
                value -> {
                  if (value == null) {
                    includeNull.set(true);
                  }
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // identity filters should always only return flowNodes of type userTask
    queryBuilder.must(Query.of(q -> q.bool(createUserTaskFlowNodeTypeFilter().build())));

    final BoolQuery.Builder identityQuery = new BoolQuery.Builder().minimumShouldMatch("1");
    if (!nonNullValues.isEmpty()) {
      identityQuery.should(
          s ->
              s.terms(
                  t ->
                      t.field(nestedFieldReference(valueField))
                          .terms(
                              tt ->
                                  tt.value(nonNullValues.stream().map(FieldValue::of).toList()))));
    }
    if (includeNull.get()) {
      identityQuery.should(
          s ->
              s.bool(
                  b -> b.mustNot(e -> e.exists(ee -> ee.field(nestedFieldReference(valueField))))));
    }

    if (MembershipFilterOperator.NOT_IN.equals(identityFilter.getOperator())) {
      queryBuilder.mustNot(m -> m.bool(identityQuery.build()));
    } else {
      queryBuilder.must(m -> m.bool(identityQuery.build()));
    }
    return queryBuilder;
  }

  private static BoolQuery.Builder createFlowNodeDurationFilterQuery(
      final FlowNodeDurationFiltersDataDto durationFilterData,
      final BoolQuery.Builder queryBuilder) {
    queryBuilder.minimumShouldMatch("1");
    durationFilterData.forEach(
        (flowNodeId, durationFilter) ->
            queryBuilder.should(
                s ->
                    s.bool(
                        b ->
                            b.must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(nestedFieldReference(FLOW_NODE_ID))
                                                    .value(flowNodeId)))
                                .must(
                                    m ->
                                        m.script(
                                            sc ->
                                                sc.script(
                                                    getDurationFilterScript(
                                                        LocalDateUtil.getCurrentDateTime()
                                                            .toInstant()
                                                            .toEpochMilli(),
                                                        nestedFieldReference(
                                                            FLOW_NODE_TOTAL_DURATION),
                                                        nestedFieldReference(FLOW_NODE_START_DATE),
                                                        durationFilter)))))));
    return queryBuilder;
  }

  private static void addFlowNodeStatusFilter(
      final BoolQuery.Builder boolQuery, final List<ProcessFilterDto<?>> filters) {
    filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .filter(
            filter -> FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass()))
        .forEach(
            filter ->
                boolQuery.filter(
                    f ->
                        f.bool(
                            FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES
                                .get(filter.getClass())
                                .apply(new BoolQuery.Builder())
                                .build())));
  }

  private static void addFlowNodeDurationFilter(
      final BoolQuery.Builder boolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, FlowNodeDurationFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            durationFilterData ->
                boolQuery.filter(
                    f -> f.bool(createFlowNodeDurationFilterQuery(durationFilterData).build())));
  }

  private static void addAssigneeFilter(
      final BoolQuery.Builder userTaskFilterBoolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, AssigneeFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            assigneeFilterData ->
                userTaskFilterBoolQuery.filter(
                    f ->
                        f.bool(
                            createAssigneeFilterQuery(assigneeFilterData, new BoolQuery.Builder())
                                .build())));
  }

  private static void addCandidateGroupFilter(
      final BoolQuery.Builder userTaskFilterBoolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, CandidateGroupFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            candidateFilterData ->
                userTaskFilterBoolQuery.filter(
                    f ->
                        f.bool(
                            createCandidateGroupFilterQuery(
                                    candidateFilterData, new BoolQuery.Builder())
                                .build())));
  }

  private static void addFlowNodeIdFilter(
      final BoolQuery.Builder boolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, ExecutedFlowNodeFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            executedFlowNodeFilterData ->
                boolQuery.filter(
                    f ->
                        f.bool(
                            createExecutedFlowNodeFilterQuery(
                                    executedFlowNodeFilterData, new BoolQuery.Builder())
                                .build())));
  }

  private static void addFlowNodeStartDateFilter(
      final BoolQuery.Builder boolQuery,
      final ZoneId timezone,
      final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, FlowNodeStartDateFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            flowNodeStartDateFilterData ->
                createFlowNodeStartDateFilterQuery(
                    flowNodeStartDateFilterData, timezone, boolQuery));
  }

  private static void addFlowNodeEndDateFilter(
      final BoolQuery.Builder boolQuery,
      final ZoneId timezone,
      final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, FlowNodeEndDateFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            flowNodeEndDateFilterData ->
                createFlowNodeEndDateFilterQuery(flowNodeEndDateFilterData, timezone, boolQuery));
  }

  private static <T extends ProcessFilterDto<?>> Stream<T> findAllViewLevelFiltersOfType(
      final List<ProcessFilterDto<?>> filters, final Class<T> filterClass) {
    return filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .filter(filterClass::isInstance)
        .map(filterClass::cast);
  }

  private static String nestedFieldReference(final String fieldName) {
    return FLOW_NODE_INSTANCES + "." + fieldName;
  }

  private static BoolQuery.Builder createFlowNodeTypeFilterQuery(
      final ProcessReportDataDto reportDataDto) {
    return createFlowNodeTypeFilterQuery(reportDataDto.isUserTaskReport());
  }

  private static BoolQuery.Builder createFlowNodeTypeFilterQuery(final boolean isUserTaskReport) {
    return isUserTaskReport ? createUserTaskFlowNodeTypeFilter() : new BoolQuery.Builder();
  }
}
