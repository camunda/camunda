/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.aggregations.AvgAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MaxAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MedianAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MinAggregation;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewPart;
import org.camunda.optimize.service.es.report.command.util.ExecutionStateAggregationUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.HashMap;
import java.util.Map;

public abstract class ProcessViewDuration extends ProcessViewPart {

  private static Map<AggregationType, AggregationStrategy> aggregationStrategyMap = new HashMap<>();

  static {
    aggregationStrategyMap.put(AggregationType.MIN, new MinAggregation());
    aggregationStrategyMap.put(AggregationType.MAX, new MaxAggregation());
    aggregationStrategyMap.put(AggregationType.AVERAGE, new AvgAggregation());
    aggregationStrategyMap.put(AggregationType.MEDIAN, new MedianAggregation());
  }

  @Override
  public AggregationBuilder createAggregation(final ProcessReportDataDto definitionData) {
    final AggregationStrategy strategy = getAggregationStrategy(definitionData);
    return strategy.getAggregationBuilder().script(getScriptedAggregationField(definitionData));
  }

  AggregationStrategy getAggregationStrategy(final ProcessReportDataDto definitionData) {
    return aggregationStrategyMap.get(definitionData.getConfiguration().getAggregationType());
  }

  private Script getScriptedAggregationField(final ProcessReportDataDto reportData) {
    return ExecutionStateAggregationUtil.getDurationAggregationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      getDurationFieldName(reportData),
      getReferenceDateFieldName(reportData)
    );
  }

  protected abstract String getReferenceDateFieldName(final ProcessReportDataDto reportData);

  protected abstract String getDurationFieldName(final ProcessReportDataDto reportData);

  @Override
  public ViewResult retrieveResult(Aggregations aggs, final ProcessReportDataDto reportData) {
    return new ViewResult(getAggregationStrategy(reportData).getValue(aggs));
  }
}
