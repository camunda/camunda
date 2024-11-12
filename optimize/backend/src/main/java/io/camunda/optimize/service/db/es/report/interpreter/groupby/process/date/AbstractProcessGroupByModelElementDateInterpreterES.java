/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public abstract class AbstractProcessGroupByModelElementDateInterpreterES
    extends AbstractProcessGroupByInterpreterES {

  private static final String ELEMENT_AGGREGATION = "elementAggregation";
  private static final String FILTERED_ELEMENTS_AGGREGATION = "filteredElements";
  private static final String MODEL_ELEMENT_TYPE_FILTER_AGGREGATION = "filteredElementsByType";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractProcessGroupByModelElementDateInterpreterES.class);

  public AbstractProcessGroupByModelElementDateInterpreterES() {}

  protected abstract DateAggregationServiceES getDateAggregationService();

  protected abstract MinMaxStatsServiceES getMinMaxStatsService();

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      final AggregateByDateUnit groupByDateUnit = getGroupByDateUnit(context.getReportData());
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDateUnit)) {
        return Optional.of(
            getMinMaxStatsService()
                .getMinMaxDateRangeForNestedField(
                    context,
                    baseQuery,
                    getIndexNames(context),
                    getDateField(),
                    getPathToElementField(),
                    Query.of(q -> q.bool(getFilterBoolQueryBuilder(context).build()))));
      }
    }
    return Optional.empty();
  }

  protected abstract String getPathToElementField();

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final AggregateByDateUnit unit = getGroupByDateUnit(context.getReportData());
    final MinMaxStatDto stats =
        getMinMaxStatsService()
            .getMinMaxDateRangeForNestedField(
                context,
                Query.of(q -> q.bool(boolQuery)),
                getIndexNames(context),
                getDateField(),
                getPathToElementField(),
                Query.of(q -> q.bool(getFilterBoolQueryBuilder(context).build())));

    final DateAggregationContextES dateAggContext =
        DateAggregationContextES.builder()
            .aggregateByDateUnit(unit)
            .dateField(getDateField())
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(getDistributedByInterpreter().createAggregations(context, boolQuery))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<Map<String, Aggregation.Builder.ContainerBuilder>>
        bucketLimitedHistogramAggregation =
            getDateAggregationService().createModelElementDateAggregation(dateAggContext);

    if (bucketLimitedHistogramAggregation.isPresent()) {
      final Map<String, Aggregation.Builder.ContainerBuilder> groupByFlowNodeDateAggregation =
          wrapInNestedElementAggregation(
              context,
              bucketLimitedHistogramAggregation.get(),
              getDistributedByInterpreter().createAggregations(context, boolQuery));
      return groupByFlowNodeDateAggregation;
    }

    return Map.of();
  }

  private Map<String, Aggregation.Builder.ContainerBuilder> wrapInNestedElementAggregation(
      final ExecutionContext<ProcessReportDataDto, ?> context,
      final Map<String, Aggregation.Builder.ContainerBuilder> aggregationToWrap,
      final Map<String, Aggregation.Builder.ContainerBuilder> distributedBySubAggregations) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .filter(getModelElementTypeFilterQuery())
            .aggregations(
                FILTERED_ELEMENTS_AGGREGATION,
                Aggregation.of(
                    a ->
                        a.filter(Query.of(q -> q.bool(getFilterBoolQueryBuilder(context).build())))
                            .aggregations(
                                aggregationToWrap.entrySet().stream()
                                    .collect(
                                        Collectors.toMap(
                                            Map.Entry::getKey, e -> e.getValue().build())))));

    // sibling aggregation next to filtered userTask agg for distributedByPart for retrieval of all
    // keys that should be present in distributedBy result via
    // enrichContextWithAllExpectedDistributedByKeys
    distributedBySubAggregations.forEach((k, v) -> builder.aggregations(k, v.build()));

    final Aggregation.Builder.ContainerBuilder b =
        new Aggregation.Builder()
            .nested(n -> n.path(getPathToElementField()))
            .aggregations(MODEL_ELEMENT_TYPE_FILTER_AGGREGATION, builder.build());
    return Map.of(ELEMENT_AGGREGATION, b);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    result.setGroups(processAggregations(response, context));
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC)));
  }

  private List<GroupByResult> processAggregations(
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregate> aggregations = response.aggregations();

    if (aggregations == null || aggregations.isEmpty()) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final NestedAggregate flowNodes = aggregations.get(ELEMENT_AGGREGATION).nested();
    final FilterAggregate filteredFlowNodesByType =
        flowNodes.aggregations().get(MODEL_ELEMENT_TYPE_FILTER_AGGREGATION).filter();
    final FilterAggregate filteredFlowNodes =
        filteredFlowNodesByType.aggregations().get(FILTERED_ELEMENTS_AGGREGATION).filter();
    final Optional<Map<String, Aggregate>> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(filteredFlowNodes.aggregations());

    getDistributedByInterpreter()
        .enrichContextWithAllExpectedDistributedByKeys(
            context, filteredFlowNodesByType.aggregations());

    final Map<String, Map<String, Aggregate>> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap =
          getDateAggregationService()
              .mapDateAggregationsToKeyAggregationMap(
                  unwrappedLimitedAggregations.get(), context.getTimezone());
    } else {
      return Collections.emptyList();
    }
    return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Map<String, Aggregate>> keyToAggregationMap,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    getDistributedByInterpreter()
                        .retrieveResult(response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  protected abstract String getDateField();

  protected abstract BoolQuery.Builder getFilterBoolQueryBuilder(
      final ExecutionContext<ProcessReportDataDto, ?> context);

  protected abstract Query getModelElementTypeFilterQuery();
}
