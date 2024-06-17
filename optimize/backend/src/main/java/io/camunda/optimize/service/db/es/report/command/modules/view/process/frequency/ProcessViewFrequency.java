/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process.frequency;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.ProcessViewPart;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;

public abstract class ProcessViewFrequency extends ProcessViewPart {

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.FREQUENCY;
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto> context) {
    return Collections.singletonList(filter(FREQUENCY_AGGREGATION, QueryBuilders.matchAllQuery()));
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
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
