/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_VARIABLE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewVariableInterpreterES
    extends AbstractProcessViewMultiAggregationInterpreterES {
  private static final String NESTED_VARIABLE_AGGREGATION = "nestedVariableAggregation";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_VARIABLE);
  }

  @Override
  public ViewProperty getViewProperty(
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final VariableViewPropertyDto variableViewDto = getVariableViewDto(context);
    return ViewProperty.VARIABLE(variableViewDto.getName(), variableViewDto.getType());
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final VariableViewPropertyDto variableViewDto = getVariableViewDto(context);
    final VariableType variableType = variableViewDto.getType();
    if (!VariableType.getNumericTypes().contains(variableType)) {
      throw new BadRequestException(
          "Only numeric variable types are supported for reports with view on variables!");
    }

    final FilterAggregationBuilder filteredVariablesAggregation =
        filter(
            FILTERED_VARIABLES_AGGREGATION,
            boolQuery()
                .must(termQuery(getNestedVariableNameField(), variableViewDto.getName()))
                .must(termQuery(getNestedVariableTypeField(), variableType.getId()))
                .must(existsQuery(getNestedVariableValueFieldForType(variableType))));
    getAggregationStrategies(context.getReportData()).stream()
        .map(
            strategy ->
                strategy
                    .createAggregationBuilder()
                    .field(getNestedVariableValueFieldForType(variableType)))
        .forEach(filteredVariablesAggregation::subAggregation);

    return Collections.singletonList(
        nested(NESTED_VARIABLE_AGGREGATION, VARIABLES)
            .subAggregation(filteredVariablesAggregation));
  }

  @Override
  public ViewResult retrieveResult(
      SearchResponse response,
      Aggregations aggregations,
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Nested nested = response.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
    final Filter filterVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);

    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    getAggregationStrategies(context.getReportData())
        .forEach(
            aggregationStrategy -> {
              final Double measureResult =
                  aggregationStrategy.getValue(filterVariables.getAggregations());
              viewResultBuilder.viewMeasure(
                  ViewMeasure.builder()
                      .aggregationType(aggregationStrategy.getAggregationType())
                      .value(measureResult)
                      .build());
            });
    return viewResultBuilder.build();
  }

  private VariableViewPropertyDto getVariableViewDto(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getView().getProperties().stream()
        .map(property -> property.getViewPropertyDtoIfOfType(VariableViewPropertyDto.class))
        .filter(Optional::isPresent)
        .map(Optional::get)
        // we take the first as only one variable view property is supported
        .findFirst()
        .orElseThrow(
            () ->
                new OptimizeRuntimeException(
                    "No variable view property found in report configuration"));
  }
}
