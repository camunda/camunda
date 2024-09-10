/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INSTANCE_PERCENTAGE;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewInstancePercentageInterpreterES implements ProcessViewInterpreterES {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_INSTANCE_PERCENTAGE);
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Collections.singletonList(filter(FREQUENCY_AGGREGATION, QueryBuilders.matchAllQuery()));
  }

  @Override
  public ViewResult retrieveResult(
      SearchResponse response,
      Aggregations aggregations,
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final long unfilteredTotalInstanceCount = context.getUnfilteredTotalInstanceCount();
    if (unfilteredTotalInstanceCount == 0) {
      return createViewResult(null);
    }
    final Filter frequency = aggregations.get(FREQUENCY_AGGREGATION);
    return createViewResult(
        ((double) frequency.getDocCount() / unfilteredTotalInstanceCount) * 100);
  }

  @Override
  public ViewResult createEmptyResult(
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    // for instance count the default is 0
    // see https://jira.camunda.com/browse/OPT-3336
    return createViewResult(0.);
  }

  private ViewResult createViewResult(final Double value) {
    return ViewResult.builder()
        .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(value).build())
        .build();
  }
}
