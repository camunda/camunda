/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter.util;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS.getDurationFilterScript;
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

import com.nimbusds.oauth2.sdk.util.CollectionUtils;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.ScriptQuery;
import org.opensearch.client.util.TriFunction;

public final class ModelElementFilterQueryUtilOS {

  private static final Map<Class<? extends ProcessFilterDto<?>>, Function<Builder, Builder>>
      FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES =
          Map.of(
              RunningFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilOS::createRunningFlowNodesOnlyFilterQuery,
              CompletedFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilOS::createCompletedFlowNodesOnlyFilterQuery,
              CompletedOrCanceledFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilOS::createCompletedOrCanceledFlowNodesOnlyFilterQuery,
              CanceledFlowNodesOnlyFilterDto.class,
              ModelElementFilterQueryUtilOS::createCanceledFlowNodesOnlyFilterQuery);

  private static final NestedDefinitionQueryBuilderOS NESTED_DEFINITION_QUERY_BUILDER =
      new NestedDefinitionQueryBuilderOS(
          FLOW_NODE_INSTANCES,
          FLOW_NODE_DEFINITION_KEY,
          FLOW_NODE_DEFINITION_VERSION,
          FLOW_NODE_TENANT_ID);

  private static final Map<
          Class<? extends ProcessFilterDto<?>>,
          TriFunction<FlowNodeDateFilterDataDto<?>, ZoneId, Builder, Builder>>
      FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES =
          Map.of(
              FlowNodeStartDateFilterDto.class,
              ModelElementFilterQueryUtilOS::createFlowNodeStartDateFilterQuery,
              FlowNodeEndDateFilterDto.class,
              ModelElementFilterQueryUtilOS::createFlowNodeEndDateFilterQuery);

  private ModelElementFilterQueryUtilOS() {}

  public static Optional<Query> instanceFilterForRelevantViewLevelFiltersQuery(
      final List<ProcessFilterDto<?>> filters, final FilterContext filterContext) {
    final List<ProcessFilterDto<?>> viewLevelFiltersForInstanceMatch =
        getViewLevelFiltersForInstanceMatch(filters);
    if (!viewLevelFiltersForInstanceMatch.isEmpty()) {
      final Builder viewFilterInstanceQueryBuilder =
          createFlowNodeTypeFilterQuery(filterContext.isUserTaskReport());
      viewLevelFiltersForInstanceMatch.forEach(
          filter -> {
            if (filter instanceof FlowNodeDurationFilterDto) {
              final FlowNodeDurationFiltersDataDto filterData =
                  (FlowNodeDurationFiltersDataDto) filter.getData();
              createFlowNodeDurationFilterQuery(filterData, viewFilterInstanceQueryBuilder);
            } else if (filter instanceof CandidateGroupFilterDto) {
              final IdentityLinkFilterDataDto filterData =
                  (IdentityLinkFilterDataDto) filter.getData();
              createCandidateGroupFilterQuery(filterData, viewFilterInstanceQueryBuilder);
            } else if (filter instanceof AssigneeFilterDto) {
              final IdentityLinkFilterDataDto filterData =
                  (IdentityLinkFilterDataDto) filter.getData();
              createAssigneeFilterQuery(filterData, viewFilterInstanceQueryBuilder);
            } else if (filter instanceof ExecutedFlowNodeFilterDto) {
              final ExecutedFlowNodeFilterDataDto filterData =
                  (ExecutedFlowNodeFilterDataDto) filter.getData();
              createExecutedFlowNodeFilterQuery(filterData, viewFilterInstanceQueryBuilder);
            } else if (FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass())) {
              FLOW_NODE_DATE_VIEW_FILTER_INSTANCE_QUERIES
                  .get(filter.getClass())
                  .apply(
                      (FlowNodeDateFilterDataDto<?>) filter.getData(),
                      filterContext.getTimezone(),
                      viewFilterInstanceQueryBuilder);
            } else {
              FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES
                  .get(filter.getClass())
                  .apply(viewFilterInstanceQueryBuilder);
            }
          });
      return Optional.of(
          nested(
              FLOW_NODE_INSTANCES,
              viewFilterInstanceQueryBuilder.build().toQuery(),
              ChildScoreMode.None));
    }
    return Optional.empty();
  }

  public static BoolQuery.Builder createModelElementAggregationFilter(
      final ProcessReportDataDto reportData,
      final FilterContext filterContext,
      final DefinitionService definitionService) {
    final BoolQuery.Builder filterBoolQueryBuilder =
        createFlowNodeTypeFilterQuery(reportData).minimumShouldMatch("1");
    final Map<String, List<ProcessFilterDto<?>>> filtersByDefinition =
        reportData.groupFiltersByDefinitionIdentifier();
    reportData
        .getDefinitions()
        .forEach(
            definitionDto -> {
              final BoolQuery.Builder flowNodeDefinitionQueryBuilder =
                  NESTED_DEFINITION_QUERY_BUILDER.createNestedDocDefinitionQuery(
                      definitionDto.getKey(),
                      definitionDto.getVersions(),
                      definitionDto.getTenantIds(),
                      definitionService);
              addModelElementFilters(
                  flowNodeDefinitionQueryBuilder,
                  filterContext,
                  filtersByDefinition.getOrDefault(definitionDto.getIdentifier(), List.of()));
              filterBoolQueryBuilder.should(flowNodeDefinitionQueryBuilder.build().toQuery());
            });

    addModelElementFilters(
        filterBoolQueryBuilder,
        filterContext,
        filtersByDefinition.getOrDefault(APPLIED_TO_ALL_DEFINITIONS, List.of()));
    return filterBoolQueryBuilder;
  }

  private static void addModelElementFilters(
      final BoolQuery.Builder filterBoolQueryBuilder,
      final FilterContext filterContext,
      final List<ProcessFilterDto<?>> filters) {
    if (filters.isEmpty()) {
      return;
    }
    addFlowNodeStatusFilter(filterBoolQueryBuilder, filters);
    addFlowNodeDurationFilter(filterBoolQueryBuilder, filters);
    addFlowNodeIdFilter(filterBoolQueryBuilder, filters);
    addFlowNodeStartDateFilter(filterBoolQueryBuilder, filterContext.getTimezone(), filters);
    addFlowNodeEndDateFilter(filterBoolQueryBuilder, filterContext.getTimezone(), filters);
    addAssigneeFilter(filterBoolQueryBuilder, filters);
    addCandidateGroupFilter(filterBoolQueryBuilder, filters);
  }

  public static BoolQuery.Builder createUserTaskFlowNodeTypeFilter() {
    return new BoolQuery.Builder()
        .must(term(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_USER_TASK));
  }

  public static Query createInclusiveFlowNodeIdFilterQuery(
      final ProcessReportDataDto reportDataDto,
      final Set<String> flowNodeIds,
      final FilterContext filterContext,
      final DefinitionService definitionService) {
    return createExecutedFlowNodeFilterQuery(
            createModelElementAggregationFilter(reportDataDto, filterContext, definitionService),
            nestedFieldReference(FLOW_NODE_ID),
            new ArrayList<>(flowNodeIds),
            IN)
        .build()
        .toQuery();
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

  public static Query createExecutedFlowNodeFilterQuery(
      final ExecutedFlowNodeFilterDataDto executedFlowNodeFilterData,
      final String nestedFieldReference,
      final BoolQuery.Builder boolQueryBuilder) {
    return createExecutedFlowNodeFilterQuery(
            boolQueryBuilder,
            nestedFieldReference,
            executedFlowNodeFilterData.getValues(),
            executedFlowNodeFilterData.getOperator())
        .build()
        .toQuery();
  }

  public static BoolQuery.Builder createExecutedFlowNodeFilterQuery(
      final BoolQuery.Builder boolQuery,
      final String nestedFieldReference,
      final List<String> flowNodeIds,
      final MembershipFilterOperator operator) {
    final Query termsQuery = stringTerms(nestedFieldReference, flowNodeIds);
    if (IN.equals(operator)) {
      boolQuery.filter(termsQuery);
    } else {
      boolQuery.filter(not(termsQuery));
    }
    return boolQuery;
  }

  public static Query createFlowNodeDurationFilterQuery(
      final FlowNodeDurationFiltersDataDto durationFilterData) {
    return createFlowNodeDurationFilterQuery(durationFilterData, new BoolQuery.Builder())
        .build()
        .toQuery();
  }

  public static BoolQuery.Builder createRunningFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .mustNot(term(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
        .mustNot(exists(nestedFieldReference(FLOW_NODE_END_DATE)));
  }

  public static BoolQuery.Builder createCompletedFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .mustNot(term(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
        .must(term(nestedFieldReference(FLOW_NODE_CANCELED), false))
        .must(exists(nestedFieldReference(FLOW_NODE_END_DATE)));
  }

  public static BoolQuery.Builder createCanceledFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .mustNot(term(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
        .must(term(nestedFieldReference(FLOW_NODE_CANCELED), true));
  }

  public static BoolQuery.Builder createCompletedOrCanceledFlowNodesOnlyFilterQuery(
      final BoolQuery.Builder boolQueryBuilder) {
    return boolQueryBuilder
        .mustNot(term(nestedFieldReference(FLOW_NODE_TYPE), FLOW_NODE_TYPE_MI_BODY))
        .must(exists(nestedFieldReference(FLOW_NODE_END_DATE)));
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
    return createFlowNodeDateFilterQuery(
        filterData, timezone, new BoolQuery.Builder(), nestedFieldReference(FLOW_NODE_END_DATE));
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
    final Optional<Query> optionalDateRangeQuery =
        DateFilterQueryUtilOS.createRangeQuery(filterData, nestedDateField, timezone);
    optionalDateRangeQuery.ifPresent(
        dateRangeQuery -> {
          if (CollectionUtils.isEmpty(filterData.getFlowNodeIds())) {
            queryBuilder.filter(dateRangeQuery);
          } else {
            queryBuilder.minimumShouldMatch("1");
            filterData
                .getFlowNodeIds()
                .forEach(
                    flowNodeId ->
                        queryBuilder.should(
                            and(
                                term(nestedFieldReference(FLOW_NODE_ID), flowNodeId),
                                dateRangeQuery)));
          }
        });
    return queryBuilder;
  }

  public static Query createAssigneeFilterQuery(final IdentityLinkFilterDataDto assigneeFilter) {
    return createIdentityLinkFilterQuery(
            assigneeFilter, USER_TASK_ASSIGNEE, new BoolQuery.Builder())
        .build()
        .toQuery();
  }

  private static BoolQuery.Builder createAssigneeFilterQuery(
      final IdentityLinkFilterDataDto assigneeFilter, final BoolQuery.Builder queryBuilder) {
    return createIdentityLinkFilterQuery(assigneeFilter, USER_TASK_ASSIGNEE, queryBuilder);
  }

  public static Query createCandidateGroupFilterQuery(
      final IdentityLinkFilterDataDto candidateGroupFilter) {
    return createIdentityLinkFilterQuery(
            candidateGroupFilter, USER_TASK_CANDIDATE_GROUPS, new BoolQuery.Builder())
        .build()
        .toQuery();
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
    queryBuilder.must(createUserTaskFlowNodeTypeFilter().build().toQuery());

    final BoolQuery.Builder identityQueryBuilder = new BoolQuery.Builder().minimumShouldMatch("1");
    if (!nonNullValues.isEmpty()) {
      identityQueryBuilder.should(stringTerms(nestedFieldReference(valueField), nonNullValues));
    }
    if (includeNull.get()) {
      identityQueryBuilder.should(not(exists(nestedFieldReference(valueField))));
    }
    final Query identityQuery = identityQueryBuilder.build().toQuery();

    if (MembershipFilterOperator.NOT_IN.equals(identityFilter.getOperator())) {
      queryBuilder.mustNot(identityQuery);
    } else {
      queryBuilder.must(identityQuery);
    }
    return queryBuilder;
  }

  private static BoolQuery.Builder createFlowNodeDurationFilterQuery(
      final FlowNodeDurationFiltersDataDto durationFilterData,
      final BoolQuery.Builder queryBuilder) {
    queryBuilder.minimumShouldMatch("1");
    durationFilterData.forEach(
        (flowNodeId, durationFilter) -> {
          final Query particularFlowNodeQuery =
              and(
                  term(nestedFieldReference(FLOW_NODE_ID), flowNodeId),
                  new ScriptQuery.Builder()
                      .script(
                          getDurationFilterScript(
                              LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
                              nestedFieldReference(FLOW_NODE_TOTAL_DURATION),
                              nestedFieldReference(FLOW_NODE_START_DATE),
                              durationFilter))
                      .build()
                      .toQuery());
          queryBuilder.should(particularFlowNodeQuery);
        });
    return queryBuilder;
  }

  private static void addFlowNodeStatusFilter(
      final BoolQuery.Builder boolQueryBuilder, final List<ProcessFilterDto<?>> filters) {
    filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .filter(
            filter -> FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES.containsKey(filter.getClass()))
        .forEach(
            filter ->
                boolQueryBuilder.filter(
                    FLOW_NODE_STATUS_VIEW_FILTER_INSTANCE_QUERIES
                        .get(filter.getClass())
                        .apply(new BoolQuery.Builder())
                        .build()
                        .toQuery()));
  }

  private static void addFlowNodeDurationFilter(
      final BoolQuery.Builder boolQueryBuilder, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, FlowNodeDurationFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            durationFilterData ->
                boolQueryBuilder.filter(createFlowNodeDurationFilterQuery(durationFilterData)));
  }

  private static void addAssigneeFilter(
      final BoolQuery.Builder userTaskFilterBoolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, AssigneeFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            assigneeFilterData ->
                userTaskFilterBoolQuery.filter(
                    createAssigneeFilterQuery(assigneeFilterData, new BoolQuery.Builder())
                        .build()
                        .toQuery()));
  }

  private static void addCandidateGroupFilter(
      final BoolQuery.Builder userTaskFilterBoolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, CandidateGroupFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            candidateFilterData ->
                userTaskFilterBoolQuery.filter(
                    createCandidateGroupFilterQuery(candidateFilterData, new BoolQuery.Builder())
                        .build()
                        .toQuery()));
  }

  private static void addFlowNodeIdFilter(
      final BoolQuery.Builder boolQuery, final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, ExecutedFlowNodeFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            executedFlowNodeFilterData ->
                boolQuery.filter(
                    createExecutedFlowNodeFilterQuery(
                            executedFlowNodeFilterData, new BoolQuery.Builder())
                        .build()
                        .toQuery()));
  }

  private static void addFlowNodeStartDateFilter(
      final BoolQuery.Builder boolQueryBuilder,
      final ZoneId timezone,
      final List<ProcessFilterDto<?>> filters) {
    findAllViewLevelFiltersOfType(filters, FlowNodeStartDateFilterDto.class)
        .map(ProcessFilterDto::getData)
        .forEach(
            flowNodeStartDateFilterData ->
                createFlowNodeStartDateFilterQuery(
                    flowNodeStartDateFilterData, timezone, boolQueryBuilder));
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
