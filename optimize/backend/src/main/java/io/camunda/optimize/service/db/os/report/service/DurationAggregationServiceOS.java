/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.service;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.filter;
import static io.camunda.optimize.service.db.os.report.filter.util.ModelElementFilterQueryUtilOS.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.wrapWithFilterLimitedParentAggregation;
import static io.camunda.optimize.service.db.os.report.interpreter.util.NumberHistogramAggregationUtilOS.generateHistogramFromScript;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.service.DurationAggregationService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DurationAggregationServiceOS extends DurationAggregationService {

  private final MinMaxStatsServiceOS minMaxStatsService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;

  public DurationAggregationServiceOS(
      final MinMaxStatsServiceOS minMaxStatsService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter) {
    this.minMaxStatsService = minMaxStatsService;
    this.distributedByInterpreter = distributedByInterpreter;
  }

  public Optional<Pair<String, Aggregation>> createLimitedGroupByScriptedDurationAggregation(
      final Query baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Script durationCalculationScript) {
    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxNumberRangeForScriptedField(
            context, baseQuery, getIndexNames(context), durationCalculationScript);
    return createLimitedGroupByScriptedDurationAggregation(
        context,
        baseQuery,
        durationCalculationScript,
        minMaxStats,
        this::createProcessInstanceLimitingFilterQuery);
  }

  public Optional<Pair<String, Aggregation>>
      createLimitedGroupByScriptedUserTaskDurationAggregation(
          final Query boolQuery,
          final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
          final Script durationCalculationScript,
          final UserTaskDurationTime userTaskDurationTime) {

    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxNumberRangeForNestedScriptedField(
            context,
            boolQuery,
            getIndexNames(context),
            FLOW_NODE_INSTANCES,
            durationCalculationScript,
            Query.of(q -> q.bool(createUserTaskFlowNodeTypeFilter().build())));
    return createLimitedGroupByScriptedDurationAggregation(
        context,
        boolQuery,
        durationCalculationScript,
        minMaxStats,
        (filterOperator, filterValueInMillis) ->
            createUserTaskLimitingFilterQuery(
                filterOperator, userTaskDurationTime, filterValueInMillis));
  }

  public Optional<Pair<String, Aggregation>> createLimitedGroupByScriptedEventDurationAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Script durationCalculationScript) {
    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxNumberRangeForNestedScriptedField(
            context, query, getIndexNames(context), FLOW_NODE_INSTANCES, durationCalculationScript);
    return createLimitedGroupByScriptedDurationAggregation(
        context,
        query,
        durationCalculationScript,
        minMaxStats,
        this::createEventLimitingFilterQuery);
  }

  public List<CompositeCommandResult.GroupByResult> mapGroupByDurationResults(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> parentSubAggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Optional.ofNullable(parentSubAggregations)
        .flatMap(FilterLimitedAggregationUtilOS::unwrapFilterLimitedAggregations)
        .map(aggregations -> aggregations.get(DURATION_HISTOGRAM_AGGREGATION).histogram())
        .stream()
        .flatMap(
            histogramAggregation ->
                histogramAggregation.buckets().array().stream()
                    .map(
                        durationBucket -> {
                          final List<DistributedByResult> distributions =
                              distributedByInterpreter.retrieveResult(
                                  response, durationBucket.aggregations(), context);
                          return CompositeCommandResult.GroupByResult.createGroupByResult(
                              String.valueOf(durationBucket.key()), distributions);
                        }))
        .toList();
  }

  private Query createUserTaskLimitingFilterQuery(
      final ComparisonOperator filterOperator,
      final UserTaskDurationTime userTaskDurationTime,
      final double filterValueInMillis) {
    return createLimitingFilterQuery(
        filterOperator,
        (long) filterValueInMillis,
        FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName(),
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE,
        // user task duration calculations can be null (e.g. work time if the userTask hasn't been
        // claimed)
        true);
  }

  private Optional<Pair<String, Aggregation>> createLimitedGroupByScriptedDurationAggregation(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery,
      final Script durationCalculationScript,
      final MinMaxStatDto minMaxStats,
      final BiFunction<ComparisonOperator, Double, Query> limitingFilterCreator) {

    if (minMaxStats.isEmpty()) {
      return Optional.empty();
    }

    final SingleReportConfigurationDto reportConfigurationDto =
        context.getReportData().getConfiguration();

    final CustomBucketDto customBucketDto = reportConfigurationDto.getCustomBucket();
    final double minValueInMillis = getMinValueInMillis(minMaxStats, customBucketDto);
    final double maxValueInMillis = minMaxStats.getMax();
    if (minValueInMillis > maxValueInMillis) {
      return Optional.empty();
    }

    final double intervalInMillis =
        getIntervalInMillis(minValueInMillis, maxValueInMillis, customBucketDto);

    final Query limitingFilter =
        filter(
            limitingFilterCreator.apply(ComparisonOperator.GREATER_THAN_EQUALS, minValueInMillis));

    final Pair<String, Aggregation> histogramAggregation =
        generateHistogramFromScript(
            DURATION_HISTOGRAM_AGGREGATION,
            intervalInMillis,
            minValueInMillis,
            durationCalculationScript,
            maxValueInMillis,
            distributedByInterpreter.createAggregations(context, baseQuery));

    return Optional.of(
        wrapWithFilterLimitedParentAggregation(limitingFilter, histogramAggregation));
  }

  private Query createProcessInstanceLimitingFilterQuery(
      final ComparisonOperator filterOperator, final double filterValueInMillis) {
    return createLimitingFilterQuery(
        filterOperator, (long) filterValueInMillis, DURATION, START_DATE, false);
  }

  private Query createLimitingFilterQuery(
      final ComparisonOperator filterOperator,
      final long filterValueInMillis,
      final String durationFieldName,
      final String referenceDateFieldName,
      final boolean includeNull) {
    return new Query.Builder()
        .script(
            b ->
                b.script(
                    DurationScriptUtilOS.getDurationFilterScript(
                        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
                        durationFieldName,
                        referenceDateFieldName,
                        DurationFilterDataDto.builder()
                            .operator(filterOperator)
                            .unit(FILTER_UNIT)
                            .value(filterValueInMillis)
                            .includeNull(includeNull)
                            .build())))
        .build();
  }

  private Query createEventLimitingFilterQuery(
      final ComparisonOperator filterOperator, final double filterValueInMillis) {
    return createLimitingFilterQuery(
        filterOperator,
        (long) filterValueInMillis,
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION,
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE,
        false);
  }
}
