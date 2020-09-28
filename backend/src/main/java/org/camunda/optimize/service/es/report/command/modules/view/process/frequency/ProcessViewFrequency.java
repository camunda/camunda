/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewPart;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;

import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

public abstract class ProcessViewFrequency extends ProcessViewPart {
  private static final String FREQUENCY_AGGREGATION = "_frequency";

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return filter(FREQUENCY_AGGREGATION, QueryBuilders.matchAllQuery());
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final Filter count = aggs.get(FREQUENCY_AGGREGATION);
    return new ViewResult().setNumber((double) count.getDocCount());
  }
}
