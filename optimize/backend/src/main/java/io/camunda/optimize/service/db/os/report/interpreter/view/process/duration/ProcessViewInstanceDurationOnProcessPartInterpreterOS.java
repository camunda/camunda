/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.duration;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INSTANCE_DURATION_PROCESS_PART;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.aggregations.AggregationStrategyOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.util.ProcessPartQueryUtilOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.duration.ProcessViewDurationInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewInstanceDurationOnProcessPartInterpreterOS
    extends ProcessViewInstanceDurationInterpreterOS {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_INSTANCE_DURATION_PROCESS_PART);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ProcessPartDto processPart = ProcessViewDurationInterpreterHelper.getProcessPart(context);
    return ProcessPartQueryUtilOS.createProcessPartAggregation(
        processPart.getStart(),
        processPart.getEnd(),
        getAggregationStrategies(context.getReportData()).stream()
            .map(AggregationStrategyOS::getAggregationType)
            .collect(Collectors.toList()));
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<AggregationStrategyOS> aggregationStrategies =
        getAggregationStrategies(context.getReportData());
    final Function<AggregationStrategyOS, Double> measureExtractor =
        aggregationStrategy ->
            ProcessPartQueryUtilOS.getProcessPartAggregationResult(
                aggs, aggregationStrategy.getAggregationType());
    return ProcessViewDurationInterpreterHelper.retrieveResult(
        aggregationStrategies, measureExtractor);
  }

  @Override
  public BoolQuery.Builder adjustQuery(
      final BoolQuery.Builder queryBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    super.adjustQuery(queryBuilder, context);
    final ProcessPartDto processPart = ProcessViewDurationInterpreterHelper.getProcessPart(context);
    ProcessPartQueryUtilOS.addProcessPartQuery(
        queryBuilder, processPart.getStart(), processPart.getEnd());
    return queryBuilder;
  }
}
