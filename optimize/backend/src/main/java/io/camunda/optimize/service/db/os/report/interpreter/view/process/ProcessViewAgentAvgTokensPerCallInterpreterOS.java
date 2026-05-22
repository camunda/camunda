/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_AVG_TOKENS_PER_CALL;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.aggregations.SumAggregationOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewAgentAvgTokensPerCallInterpreterOS implements ProcessViewInterpreterOS {

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

  private final SumAggregationOS sumAggregation = new SumAggregationOS();

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_AGENT_AVG_TOKENS_PER_CALL);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Script totalTokensScript = OpenSearchWriterUtil.createDefaultScript(TOTAL_TOKENS_SCRIPT);
    final Script totalModelCallsScript =
        OpenSearchWriterUtil.createDefaultScript(TOTAL_MODEL_CALLS_SCRIPT);

    return Stream.of(
            sumAggregation.createAggregation(TOTAL_TOKENS_IDENTIFIER, totalTokensScript),
            sumAggregation.createAggregation(TOTAL_MODEL_CALLS_IDENTIFIER, totalModelCallsScript))
        .collect(MapUtil.pairCollector());
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Double totalTokens = sumAggregation.getValue(TOTAL_TOKENS_IDENTIFIER, aggregations);
    final Double totalModelCalls =
        sumAggregation.getValue(TOTAL_MODEL_CALLS_IDENTIFIER, aggregations);
    return createViewResult(calculateRatio(totalTokens, totalModelCalls));
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return createViewResult(null);
  }

  private ViewResult createViewResult(final Double value) {
    return ViewResult.builder().viewMeasure(ViewMeasure.builder().value(value).build()).build();
  }

  private Double calculateRatio(final Double numerator, final Double denominator) {
    if (numerator == null || denominator == null || denominator == 0.0) {
      return null;
    }
    return numerator / denominator;
  }
}
