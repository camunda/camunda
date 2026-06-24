/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.duration;

import static io.camunda.optimize.service.db.es.report.interpreter.util.ProcessPartQueryUtilES.addProcessPartQuery;
import static io.camunda.optimize.service.db.es.report.interpreter.util.ProcessPartQueryUtilES.createProcessPartAggregation;
import static io.camunda.optimize.service.db.es.report.interpreter.util.ProcessPartQueryUtilES.getProcessPartAggregationResult;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_INSTANCE_DURATION_PROCESS_PART;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.aggregations.AggregationStrategyES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.duration.ProcessViewDurationInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewInstanceDurationOnProcessPartInterpreterES
    extends ProcessViewInstanceDurationInterpreterES {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_INSTANCE_DURATION_PROCESS_PART);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ProcessPartDto processPart = ProcessViewDurationInterpreterHelper.getProcessPart(context);
    return createProcessPartAggregation(
        processPart.getStart(),
        processPart.getEnd(),
        getAggregationStrategies(context.getReportData()).stream()
            .map(AggregationStrategyES::getAggregationType)
            .collect(Collectors.toList()));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<AggregationStrategyES<?>> aggregationStrategies =
        getAggregationStrategies(context.getReportData());
    final Function<AggregationStrategyES<?>, Double> measureExtractor =
        aggregationStrategy ->
            getProcessPartAggregationResult(aggs, aggregationStrategy.getAggregationType());
    return ProcessViewDurationInterpreterHelper.retrieveResult(
        aggregationStrategies, measureExtractor);
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    super.adjustSearchRequest(searchRequestBuilder, baseQueryBuilder, context);
    final ProcessPartDto processPart = ProcessViewDurationInterpreterHelper.getProcessPart(context);
    addProcessPartQuery(baseQueryBuilder, processPart.getStart(), processPart.getEnd());
  }
}
