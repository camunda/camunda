/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewPart;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;

import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FREQUENCY_AGGREGATION;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

public abstract class ProcessViewFrequency extends ProcessViewPart {

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.FREQUENCY;
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    return Collections.singletonList(filter(FREQUENCY_AGGREGATION, QueryBuilders.matchAllQuery()));
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final Filter count = aggs.get(FREQUENCY_AGGREGATION);
    return createViewResult((double) count.getDocCount());
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    return createViewResult(null);
  }

  public ViewResult createViewResult(final Double value) {
    return ViewResult.builder()
      .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(value).build())
      .build();
  }
}
