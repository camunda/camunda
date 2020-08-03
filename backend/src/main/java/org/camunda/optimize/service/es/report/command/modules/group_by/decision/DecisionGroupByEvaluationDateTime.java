/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.decision.util.DecisionInstanceQueryUtil;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.util.DateAggregationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.isResultComplete;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByEvaluationDateTime extends GroupByPart<DecisionReportDataDto> {

  private final DateAggregationService dateAggregationService;
  private final DecisionQueryFilterEnhancer queryFilterEnhancer;
  private final OptimizeElasticsearchClient esClient;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<DecisionReportDataDto> context) {
    final GroupByDateUnit unit = getGroupBy(context.getReportData()).getUnit();
    return createAggregation(searchSourceBuilder, context, unit);
  }

  private List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                     final ExecutionContext<DecisionReportDataDto> context,
                                                     final GroupByDateUnit unit) {
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return createAutomaticIntervalAggregation(searchSourceBuilder, context);
    }

    return Collections.singletonList(
      dateAggregationService.createFilterLimitedDecisionDateHistogramWithSubAggregation(
        unit,
        EVALUATION_DATE_TIME,
        context.getTimezone(),
        context.getReportData().getFilter(),
        DecisionInstanceQueryUtil.getLatestEvaluationDate(searchSourceBuilder.query(), esClient).orElse(null),
        queryFilterEnhancer,
        distributedByPart.createAggregation(context)
      ));
  }

  private List<AggregationBuilder> createAutomaticIntervalAggregation(final SearchSourceBuilder builder,
                                                                      final ExecutionContext<DecisionReportDataDto> context) {
    Optional<AggregationBuilder> automaticIntervalAggregation =
      dateAggregationService.createAutomaticIntervalAggregation(
        builder.query(),
        DECISION_INSTANCE_INDEX_NAME,
        EVALUATION_DATE_TIME,
        context.getTimezone()
      );

    return automaticIntervalAggregation.map(agg -> agg.subAggregation(distributedByPart.createAggregation(context)))
      .map(Collections::singletonList)
      .orElseGet(() -> createAggregation(builder, context, GroupByDateUnit.MONTH));
  }

  @Override
  public void addQueryResult(final CompositeCommandResult result,
                             final SearchResponse response,
                             final ExecutionContext<DecisionReportDataDto> context) {
    result.setGroups(processAggregations(response, response.getAggregations(), context));
    result.setIsComplete(isResultComplete(response));
    result.setSorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC))
    );
  }

  private DecisionGroupByEvaluationDateTimeValueDto getGroupBy(final DecisionReportDataDto reportData) {
    return ((DecisionGroupByEvaluationDateTimeDto) reportData.getGroupBy())
      .getValue();
  }

  private List<GroupByResult> processAggregations(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<DecisionReportDataDto> context) {
    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap = dateAggregationService.mapHistogramAggregationsToKeyAggregationMap(
        unwrappedLimitedAggregations.get(),
        context.getTimezone()
      );
    } else {
      keyToAggregationMap = dateAggregationService.mapRangeAggregationsToKeyAggregationMap(
        aggregations,
        context.getTimezone()
      );
    }
    return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(final Map<String, Aggregations> keyToAggregationMap,
                                                             final SearchResponse response,
                                                             final ExecutionContext<DecisionReportDataDto> context) {
    return keyToAggregationMap
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        stringBucketEntry.getKey(),
        distributedByPart.retrieveResult(response, stringBucketEntry.getValue(), context)
      ))
      .collect(Collectors.toList());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto reportData) {
    reportData.setGroupBy(new DecisionGroupByEvaluationDateTimeDto());
  }
}
