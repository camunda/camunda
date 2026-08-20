/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.filterAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INSTANCE_PERCENTAGE;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewInstancePercentageInterpreterOS implements ProcessViewInterpreterOS {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_INSTANCE_PERCENTAGE);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Map.of(FREQUENCY_AGGREGATION, filterAggregation(matchAll()));
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final long unfilteredTotalInstanceCount = context.getUnfilteredTotalInstanceCount();
    if (unfilteredTotalInstanceCount == 0) {
      return createViewResult(null);
    }
    final FilterAggregate frequency = aggregations.get(FREQUENCY_AGGREGATION).filter();
    return createViewResult((((double) frequency.docCount() / unfilteredTotalInstanceCount) * 100));
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
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
