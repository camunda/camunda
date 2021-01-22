/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.service.DurationAggregationService;
import org.camunda.optimize.service.es.report.command.util.AggregationFilterUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByFlowNodeDuration extends AbstractGroupByFlowNode {

  private final MinMaxStatsService minMaxStatsService;
  private final DurationAggregationService durationAggregationService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    return durationAggregationService
      .createLimitedGroupByScriptedEventDurationAggregation(
        searchSourceBuilder, context, distributedByPart, getDurationScript()
      )
      .map(durationAggregation -> createFilteredFlowNodeAggregation(
        context,
        durationAggregation
      ))
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    compositeCommandResult.setKeyIsOfNumericType(distributedByPart.isKeyOfNumericType(context).orElse(true));
    getFilteredFlowNodesAggregation(response)
      .ifPresent(filteredFlowNodes -> {
        final List<CompositeCommandResult.GroupByResult> durationHistogramData =
          durationAggregationService.mapGroupByDurationResults(
            response,
            filteredFlowNodes.getAggregations(),
            context,
            distributedByPart
          );

        compositeCommandResult.setGroups(durationHistogramData);
      });
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new DurationGroupByDto());
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                                                final BoolQueryBuilder baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(baseQuery));
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(final QueryBuilder baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(
      baseQuery, PROCESS_INSTANCE_INDEX_NAME, EVENTS, getDurationScript()
    );
  }

  private Script getDurationScript() {
    return AggregationFilterUtil.getDurationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      EVENTS + "." + ACTIVITY_DURATION,
      EVENTS + "." + ACTIVITY_START_DATE
    );
  }

}
