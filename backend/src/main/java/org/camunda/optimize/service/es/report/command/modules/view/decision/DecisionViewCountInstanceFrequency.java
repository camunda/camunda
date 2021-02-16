/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.decision;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionViewCountInstanceFrequency extends DecisionViewPart {
  private static final String COUNT_AGGREGATION = "_count";

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<DecisionReportDataDto> context) {
    return filter(COUNT_AGGREGATION, QueryBuilders.matchAllQuery());
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response, final Aggregations aggs,
                                   final ExecutionContext<DecisionReportDataDto> context) {
    final Filter count = aggs.get(COUNT_AGGREGATION);
    return new ViewResult().setNumber((double) count.getDocCount());
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto dataForCommandKey) {
    DecisionViewDto view = new DecisionViewDto();
    view.setProperty(ViewProperty.FREQUENCY);
    dataForCommandKey.setView(view);
  }
}
