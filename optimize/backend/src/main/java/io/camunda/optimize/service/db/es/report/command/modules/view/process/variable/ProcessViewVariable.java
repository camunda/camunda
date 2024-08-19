/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process.variable;

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
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.ProcessViewMultiAggregation;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import jakarta.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewVariable extends ProcessViewMultiAggregation {

  private static final String NESTED_VARIABLE_AGGREGATION = "nestedVariableAggregation";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProcessViewVariable.class);

  public ProcessViewVariable() {}

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    final VariableViewPropertyDto variableViewDto = getVariableViewDto(context);
    return ViewProperty.VARIABLE(variableViewDto.getName(), variableViewDto.getType());
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto> context) {
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
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<ProcessReportDataDto> context) {
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

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(
        new ProcessViewDto(ProcessViewEntity.VARIABLE, ViewProperty.VARIABLE(null, null)));
  }

  private VariableViewPropertyDto getVariableViewDto(
      final ExecutionContext<ProcessReportDataDto> context) {
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
