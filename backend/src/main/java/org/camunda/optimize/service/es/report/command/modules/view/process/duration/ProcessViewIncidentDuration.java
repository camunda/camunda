/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_CREATE_TIME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_DURATION_IN_MS;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewIncidentDuration extends ProcessViewDuration {

  private static final String NESTED_INCIDENT_AGGREGATION = "nestedIncidentAggregation";

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return nested(NESTED_INCIDENT_AGGREGATION, INCIDENTS)
      .subAggregation(super.createAggregation(context));
  }

  @Override
  public CompositeCommandResult.ViewResult retrieveResult(final SearchResponse response, final Aggregations aggs,
                                                          final ExecutionContext<ProcessReportDataDto> context) {
    final Nested nested = response.getAggregations().get(NESTED_INCIDENT_AGGREGATION);
    return super.retrieveResult(response, nested.getAggregations(), context);
  }

  @Override
  protected String getReferenceDateFieldName(final ProcessReportDataDto reportData) {
    return INCIDENTS + "." + INCIDENT_CREATE_TIME;
  }

  @Override
  protected String getDurationFieldName(final ProcessReportDataDto definitionData) {
    return INCIDENTS + "." + INCIDENT_DURATION_IN_MS;
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.INCIDENT);
    view.setProperty(ProcessViewProperty.DURATION);
    dataForCommandKey.setView(view);
  }
}
