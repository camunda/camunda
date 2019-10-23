/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;

import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

public abstract class FrequencyCountView extends ViewPart {
  private static final String COUNT_AGGREGATION = "_count";

  @Override
  public AggregationBuilder createAggregation(final ProcessReportDataDto definitionData) {
    return filter(COUNT_AGGREGATION, QueryBuilders.matchAllQuery());
  }

  @Override
  public ViewResult retrieveResult(Aggregations aggs, final ProcessReportDataDto reportData) {
    final Filter count = aggs.get(COUNT_AGGREGATION);
    return new ViewResult(count.getDocCount());
  }
}
