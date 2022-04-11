/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.service.DurationAggregationService;
import org.camunda.optimize.service.es.report.command.util.DurationScriptUtil;
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

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByFlowNodeDuration extends AbstractGroupByFlowNode {

  private final MinMaxStatsService minMaxStatsService;
  private final DurationAggregationService durationAggregationService;

  public ProcessGroupByFlowNodeDuration(final MinMaxStatsService minMaxStatsService,
                                        final DurationAggregationService durationAggregationService,
                                        final DefinitionService definitionService) {
    super(definitionService);
    this.minMaxStatsService = minMaxStatsService;
    this.durationAggregationService = durationAggregationService;
  }

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
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(distributedByPart.isKeyOfNumericType(context));
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
    return Optional.of(retrieveMinMaxDurationStats(context, baseQuery));
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(final ExecutionContext<ProcessReportDataDto> context,
                                                    final QueryBuilder baseQuery) {
    return minMaxStatsService.getScriptedMinMaxStats(
      baseQuery, getIndexNames(context), FLOW_NODE_INSTANCES, getDurationScript()
    );
  }

  private Script getDurationScript() {
    return DurationScriptUtil.getDurationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION,
      FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE
    );
  }

}
