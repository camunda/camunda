/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.decision;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FREQUENCY_AGGREGATION;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionViewInstanceFrequency extends DecisionViewPart {

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<DecisionReportDataDto> context) {
    return ViewProperty.FREQUENCY;
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<DecisionReportDataDto> context) {
    return Collections.singletonList(filter(FREQUENCY_AGGREGATION, QueryBuilders.matchAllQuery()));
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response, final Aggregations aggs,
                                   final ExecutionContext<DecisionReportDataDto> context) {
    final Filter count = aggs.get(FREQUENCY_AGGREGATION);
    return createViewResult(count.getDocCount());
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<DecisionReportDataDto> context) {
    // for instance count the default is 0
    // see https://jira.camunda.com/browse/OPT-3336
    return createViewResult(0.);
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new DecisionViewDto(ViewProperty.FREQUENCY));
  }

  private ViewResult createViewResult(final double value) {
    return ViewResult.builder()
      .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(value).build())
      .build();
  }
}
