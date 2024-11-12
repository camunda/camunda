/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_VARIABLE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueFieldForType;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.ProcessViewVariableInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import jakarta.ws.rs.BadRequestException;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessViewVariableInterpreterOS
    extends AbstractProcessViewMultiAggregationInterpreterOS {

  private static final String NESTED_VARIABLE_AGGREGATION = "nestedVariableAggregation";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_VARIABLE);
  }

  @Override
  public ViewProperty getViewProperty(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final VariableViewPropertyDto variableViewDto =
        ProcessViewVariableInterpreterHelper.getVariableViewDto(context);
    return ViewProperty.variable(variableViewDto.getName(), variableViewDto.getType());
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final VariableViewPropertyDto variableViewDto =
        ProcessViewVariableInterpreterHelper.getVariableViewDto(context);
    final VariableType variableType = variableViewDto.getType();
    if (!VariableType.getNumericTypes().contains(variableType)) {
      throw new BadRequestException(
          "Only numeric variable types are supported for reports with view on variables!");
    }

    final Map<String, Aggregation> aggs =
        getAggregationStrategies(context.getReportData()).stream()
            .map(
                strategy ->
                    strategy.createAggregation(
                        null, getNestedVariableValueFieldForType(variableType)))
            .collect(MapUtil.pairCollector());

    final Aggregation filteredVariablesAggregation =
        new Aggregation.Builder()
            .filter(
                and(
                    term(getNestedVariableNameField(), variableViewDto.getName()),
                    term(getNestedVariableTypeField(), variableType.getId()),
                    exists(getNestedVariableValueFieldForType(variableType))))
            .aggregations(aggs)
            .build();

    final Aggregation aggregation =
        new Aggregation.Builder()
            .nested(n -> n.path(VARIABLES))
            .aggregations(FILTERED_VARIABLES_AGGREGATION, filteredVariablesAggregation)
            .build();

    return Map.of(NESTED_VARIABLE_AGGREGATION, aggregation);
  }

  @Override
  public CompositeCommandResult.ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final NestedAggregate nested =
        response.aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
    final FilterAggregate filterVariables =
        nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();

    final CompositeCommandResult.ViewResult.ViewResultBuilder viewResultBuilder =
        CompositeCommandResult.ViewResult.builder();
    getAggregationStrategies(context.getReportData())
        .forEach(
            aggregationStrategy -> {
              final Double measureResult =
                  aggregationStrategy.getValue(filterVariables.aggregations());
              viewResultBuilder.viewMeasure(
                  CompositeCommandResult.ViewMeasure.builder()
                      .aggregationType(aggregationStrategy.getAggregationType())
                      .value(measureResult)
                      .build());
            });
    return viewResultBuilder.build();
  }
}
