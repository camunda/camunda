/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.variable;

import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.aggregations.AvgAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MaxAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MedianAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MinAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.SumAggregation;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewPart;
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
import java.util.Map;

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
public class ProcessViewVariable extends ProcessViewPart {

  private static final String NESTED_VARIABLE_AGGREGATION = "nestedVariableAggregation";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";

  private static final Map<AggregationType, AggregationStrategy> aggregationStrategyMap =
    ImmutableMap.<AggregationType, AggregationStrategy>builder()
      .put(AggregationType.MIN, new MinAggregation())
      .put(AggregationType.MAX, new MaxAggregation())
      .put(AggregationType.AVERAGE, new AvgAggregation())
      .put(AggregationType.MEDIAN, new MedianAggregation())
      .put(AggregationType.SUM, new SumAggregation())
      .build();

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    if (!VariableType.getNumericTypes().contains(getVariableType(context))) {
      throw new BadRequestException(
        "Only numeric variable types are supported for reports with view on variables!"
      );
    }
    final FilterAggregationBuilder filteredVariables = filter(
      FILTERED_VARIABLES_AGGREGATION,
      boolQuery()
        .must(termQuery(getNestedVariableNameField(), getVariableName(context)))
        .must(termQuery(getNestedVariableTypeField(), getVariableType(context).getId()))
        .must(existsQuery(getNestedVariableValueFieldForType(getVariableType(context))))
    );
    return nested(NESTED_VARIABLE_AGGREGATION, VARIABLES)
      .subAggregation(
        filteredVariables
          .subAggregation(
            getAggregationStrategy(context.getReportData()).getAggregationBuilder()
              .field(getNestedVariableValueFieldForType(getVariableType(context)))
          )
      );
  }

  private AggregationStrategy getAggregationStrategy(final ProcessReportDataDto definitionData) {
    return aggregationStrategyMap.get(definitionData.getConfiguration().getAggregationType());
  }

  private VariableType getVariableType(final ExecutionContext<ProcessReportDataDto> context) {
    return getVariableViewDto(context).getType();
  }

  private String getVariableName(final ExecutionContext<ProcessReportDataDto> context) {
    return getVariableViewDto(context).getName();
  }

  private VariableViewPropertyDto getVariableViewDto(final ExecutionContext<ProcessReportDataDto> context) {
    return getVariableGroupBy(context);
  }

  private VariableViewPropertyDto getVariableGroupBy(final ExecutionContext<ProcessReportDataDto> context) {
    return (VariableViewPropertyDto) context.getReportData().getView().getProperty().getViewPropertyDto();
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response, final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final Nested nested = response.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
    final Filter filterVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Double number = getAggregationStrategy(context.getReportData()).getValue(filterVariables.getAggregations());
    return new ViewResult().setNumber(number);
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new ProcessViewDto(ProcessViewEntity.VARIABLE, ViewProperty.VARIABLE(null, null)));
  }
}
