/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.service;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.wrapWithFilterLimitedParentAggregation;
import static io.camunda.optimize.service.db.es.report.interpreter.util.NumberHistogramAggregationUtilES.generateHistogramFromScript;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.HistogramAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.HistogramBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES;
import io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.service.DurationAggregationService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DurationAggregationServiceES extends DurationAggregationService {

  private final MinMaxStatsServiceES minMaxStatsService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;

  public DurationAggregationServiceES(
      final MinMaxStatsServiceES minMaxStatsService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter) {
    this.minMaxStatsService = minMaxStatsService;
    this.distributedByInterpreter = distributedByInterpreter;
  }

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createLimitedGroupByScriptedDurationAggregation(
          final BoolQuery boolQuery,
          final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
          final Script durationCalculationScript) {

    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxNumberRangeForScriptedField(
            context,
            Query.of(q -> q.bool(boolQuery)),
            getIndexNames(context),
            durationCalculationScript);
    return createLimitedGroupByScriptedDurationAggregation(
        context,
        boolQuery,
        durationCalculationScript,
        minMaxStats,
        this::createProcessInstanceLimitingFilterQuery);
  }

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createLimitedGroupByScriptedEventDurationAggregation(
          final BoolQuery boolQuery,
          final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
          final Script durationCalculationScript) {

    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxNumberRangeForNestedScriptedField(
            context,
            Query.of(q -> q.bool(boolQuery)),
            getIndexNames(context),
            FLOW_NODE_INSTANCES,
            durationCalculationScript);
    return createLimitedGroupByScriptedDurationAggregation(
        context,
        boolQuery,
        durationCalculationScript,
        minMaxStats,
        this::createEventLimitingFilterQuery);
  }

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createLimitedGroupByScriptedUserTaskDurationAggregation(
          final BoolQuery boolQuery,
          final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
          final Script durationCalculationScript,
          final UserTaskDurationTime userTaskDurationTime) {

    final MinMaxStatDto minMaxStats =
        minMaxStatsService.getMinMaxNumberRangeForNestedScriptedField(
            context,
            Query.of(q -> q.bool(boolQuery)),
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

  public List<CompositeCommandResult.GroupByResult> mapGroupByDurationResults(
      final ResponseBody<?> response,
      final Map<String, Aggregate> parentSubAggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<CompositeCommandResult.GroupByResult> durationHistogramData = new ArrayList<>();
    final Optional<HistogramAggregate> histogramAggregationResult =
        Optional.ofNullable(parentSubAggregations)
            .flatMap(FilterLimitedAggregationUtilES::unwrapFilterLimitedAggregations)
            .map(aggregations -> aggregations.get(DURATION_HISTOGRAM_AGGREGATION).histogram());

    if (histogramAggregationResult.isPresent()) {
      for (final HistogramBucket durationBucket :
          histogramAggregationResult.get().buckets().array()) {
        final List<CompositeCommandResult.DistributedByResult> distributions =
            distributedByInterpreter.retrieveResult(
                response, durationBucket.aggregations(), context);
        durationHistogramData.add(
            CompositeCommandResult.GroupByResult.createGroupByResult(
                Double.toString(durationBucket.key()), distributions));
      }
    }
    return durationHistogramData;
  }

  private Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
      createLimitedGroupByScriptedDurationAggregation(
          final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
          final BoolQuery baseQuery,
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
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.filter(
                            limitingFilterCreator.apply(
                                ComparisonOperator.GREATER_THAN_EQUALS, minValueInMillis))));

    final Aggregation.Builder.ContainerBuilder builder =
        generateHistogramFromScript(
            DURATION_HISTOGRAM_AGGREGATION,
            intervalInMillis,
            minValueInMillis,
            durationCalculationScript,
            maxValueInMillis,
            distributedByInterpreter.createAggregations(context, baseQuery));

    return Optional.of(
        wrapWithFilterLimitedParentAggregation(
            limitingFilter, Map.of(DURATION_HISTOGRAM_AGGREGATION, builder)));
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

  private Query createEventLimitingFilterQuery(
      final ComparisonOperator filterOperator, final double filterValueInMillis) {
    return createLimitingFilterQuery(
        filterOperator,
        (long) filterValueInMillis,
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION,
        FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE,
        false);
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
    return Query.of(
        q ->
            q.script(
                s ->
                    s.script(
                        DurationScriptUtilES.getDurationFilterScript(
                            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
                            durationFieldName,
                            referenceDateFieldName,
                            DurationFilterDataDto.builder()
                                .operator(filterOperator)
                                .unit(FILTER_UNIT)
                                .value(filterValueInMillis)
                                .includeNull(includeNull)
                                .build()))));
  }
}
