/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.incident;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewPart;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewIncidentFrequency extends ProcessViewPart {

  private static final String NESTED_INCIDENT_AGGREGATION = "nestedIncidentAggregation";
  private static final String INCIDENT_COUNT_AGGREGATION = "incidentCountAggregation";

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return nested(NESTED_INCIDENT_AGGREGATION, INCIDENTS)
      .subAggregation(
        count(INCIDENT_COUNT_AGGREGATION)
          .field(INCIDENTS + "." + ProcessInstanceIndex.INCIDENT_ID)
      );
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response, final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final Nested nested = response.getAggregations().get(NESTED_INCIDENT_AGGREGATION);
    ValueCount countAggregator = nested.getAggregations()
      .get(INCIDENT_COUNT_AGGREGATION);
    return new ViewResult().setNumber(countAggregator.value());
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setView(new ProcessViewDto(ProcessViewEntity.INCIDENT, ProcessViewProperty.FREQUENCY));
  }
}
