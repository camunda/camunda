/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_AVG_TOKENS_PER_CALL;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.aggregations.SumAggregationES;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewAgentAvgTokensPerCallInterpreterES implements ProcessViewInterpreterES {

  private static final String TOTAL_TOKENS_IDENTIFIER = "totalTokens";
  private static final String TOTAL_MODEL_CALLS_IDENTIFIER = "totalModelCalls";
  private static final String TOTAL_TOKENS_SCRIPT =
      "long inputTokens = doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_INPUT_TOKENS
          + "'].empty ? 0L : doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_INPUT_TOKENS
          + "'].value;"
          + "long outputTokens = doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_OUTPUT_TOKENS
          + "'].empty ? 0L : doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_OUTPUT_TOKENS
          + "'].value;"
          + "return inputTokens + outputTokens;";
  private static final String TOTAL_MODEL_CALLS_SCRIPT =
      "return doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_MODEL_CALLS
          + "'].empty ? 0L : doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_MODEL_CALLS
          + "'].value;";

  private final SumAggregationES sumAggregation = new SumAggregationES();

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_AGENT_AVG_TOKENS_PER_CALL);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Script totalTokensScript =
        ElasticsearchWriterUtil.createDefaultScript(TOTAL_TOKENS_SCRIPT);
    final Script totalModelCallsScript =
        ElasticsearchWriterUtil.createDefaultScript(TOTAL_MODEL_CALLS_SCRIPT);

    return Stream.of(
            sumAggregation.createAggregationBuilder(TOTAL_TOKENS_IDENTIFIER, totalTokensScript),
            sumAggregation.createAggregationBuilder(
                TOTAL_MODEL_CALLS_IDENTIFIER, totalModelCallsScript))
        .collect(Collectors.toMap(Pair::key, Pair::value));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Double totalTokens = sumAggregation.getValue(TOTAL_TOKENS_IDENTIFIER, aggs);
    final Double totalModelCalls = sumAggregation.getValue(TOTAL_MODEL_CALLS_IDENTIFIER, aggs);
    return createViewResult(calculateRatio(totalTokens, totalModelCalls));
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return createViewResult(null);
  }

  static Double calculateRatio(final Double numerator, final Double denominator) {
    if (numerator == null || denominator == null || denominator == 0.0) {
      return null;
    }
    return numerator / denominator;
  }

  private ViewResult createViewResult(final Double value) {
    return ViewResult.builder().viewMeasure(ViewMeasure.builder().value(value).build()).build();
  }
}
