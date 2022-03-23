/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewMultiAggregation;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewVariable extends ProcessViewMultiAggregation {

  private static final String NESTED_VARIABLE_AGGREGATION = "nestedVariableAggregation";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    final VariableViewPropertyDto variableViewDto = getVariableViewDto(context);
    return ViewProperty.VARIABLE(variableViewDto.getName(), variableViewDto.getType());
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    final VariableViewPropertyDto variableViewDto = getVariableViewDto(context);
    final VariableType variableType = variableViewDto.getType();
    if (!VariableType.getNumericTypes().contains(variableType)) {
      throw new BadRequestException(
        "Only numeric variable types are supported for reports with view on variables!"
      );
    }

    final FilterAggregationBuilder filteredVariablesAggregation = filter(
      FILTERED_VARIABLES_AGGREGATION,
      boolQuery()
        .must(termQuery(getNestedVariableNameField(), variableViewDto.getName()))
        .must(termQuery(getNestedVariableTypeField(), variableType.getId()))
        .must(existsQuery(getNestedVariableValueFieldForType(variableType)))
    );
    getAggregationStrategies(context.getReportData()).stream()
      .map(strategy -> strategy.createAggregationBuilder().field(getNestedVariableValueFieldForType(variableType)))
      .forEach(filteredVariablesAggregation::subAggregation);

    return Collections.singletonList(
      nested(NESTED_VARIABLE_AGGREGATION, VARIABLES).subAggregation(filteredVariablesAggregation)
    );
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final Nested nested = response.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
    final Filter filterVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);

    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    getAggregationStrategies(context.getReportData()).forEach(aggregationStrategy -> {
      final Double measureResult = aggregationStrategy.getValue(filterVariables.getAggregations());
      viewResultBuilder.viewMeasure(
        ViewMeasure.builder().aggregationType(aggregationStrategy.getAggregationType()).value(measureResult).build()
      );
    });
    return viewResultBuilder.build();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new ProcessViewDto(ProcessViewEntity.VARIABLE, ViewProperty.VARIABLE(null, null)));
  }

  private VariableViewPropertyDto getVariableViewDto(final ExecutionContext<ProcessReportDataDto> context) {
    return context.getReportData().getView().getProperties()
      .stream()
      .map(property -> property.getViewPropertyDtoIfOfType(VariableViewPropertyDto.class))
      .filter(Optional::isPresent)
      .map(Optional::get)
      // we take the first as only one variable view property is supported
      .findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No variable view property found in report configuration"));
  }

}
