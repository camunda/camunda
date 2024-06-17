/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process.percentage;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.ProcessViewPart;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewInstancePercentage extends ProcessViewPart {

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.PERCENTAGE;
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto> context) {
    return Collections.singletonList(filter(FREQUENCY_AGGREGATION, QueryBuilders.matchAllQuery()));
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<ProcessReportDataDto> context) {
    final long unfilteredTotalInstanceCount = context.getUnfilteredTotalInstanceCount();
    if (unfilteredTotalInstanceCount == 0) {
      return createViewResult(null);
    }
    final Filter frequency = aggs.get(FREQUENCY_AGGREGATION);
    return createViewResult(
        ((double) frequency.getDocCount() / unfilteredTotalInstanceCount) * 100);
  }

  public ViewResult createViewResult(final Double value) {
    return ViewResult.builder()
        .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(value).build())
        .build();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.PROCESS_INSTANCE);
    view.setProperties(ViewProperty.PERCENTAGE);
    dataForCommandKey.setView(view);
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    // for instance count the default is 0
    // see https://jira.camunda.com/browse/OPT-3336
    return createViewResult(0.);
  }
}
