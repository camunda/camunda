/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.service.DurationAggregationService;
import org.camunda.optimize.service.es.report.command.util.DurationScriptUtil;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
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

@Component
@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByDuration extends ProcessGroupByPart {
  private final DurationAggregationService durationAggregationService;
  private final MinMaxStatsService minMaxStatsService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final Script durationScript = getDurationScript();
    return durationAggregationService
      .createLimitedGroupByScriptedDurationAggregation(searchSourceBuilder, context, distributedByPart, durationScript)
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    final List<GroupByResult> durationHistogramData = durationAggregationService.mapGroupByDurationResults(
      response,
      response.getAggregations(),
      context,
      distributedByPart
    );

    compositeCommandResult.setGroups(durationHistogramData);
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(distributedByPart.isKeyOfNumericType(context));
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                                                final BoolQueryBuilder baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(context, baseQuery));
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new DurationGroupByDto());
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(final ExecutionContext<ProcessReportDataDto> context,
                                                    final QueryBuilder baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(
      baseQuery,
      getIndexNames(context),
      null,
      getDurationScript()
    );
  }

  private Script getDurationScript() {
    return DurationScriptUtil.getDurationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      ProcessInstanceIndex.DURATION,
      ProcessInstanceIndex.START_DATE
    );
  }

}
