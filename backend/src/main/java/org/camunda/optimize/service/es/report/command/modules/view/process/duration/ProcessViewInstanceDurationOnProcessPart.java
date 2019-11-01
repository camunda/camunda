/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.addProcessPartQuery;
import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.createProcessPartAggregation;
import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.processProcessPartAggregationOperations;

@Component()
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessViewInstanceDurationOnProcessPart extends ProcessViewInstanceDuration {

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    ProcessPartDto processPart = context.getReportConfiguration()
      .getProcessPart()
      .orElseThrow(() -> new OptimizeRuntimeException("Missing ProcessPart"));
    return createProcessPartAggregation(processPart.getStart(), processPart.getEnd());
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final Long durationInMs = processProcessPartAggregationOperations(
      aggs,
      getAggregationStrategy(context.getReportData()).getAggregationType()
    );
    return new ViewResult().setNumber(durationInMs);
  }

  @Override
  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<ProcessReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);
    ProcessPartDto processPart = context.getReportConfiguration().getProcessPart()
      .orElseThrow(() -> new OptimizeRuntimeException("Missing ProcessPart"));
    addProcessPartQuery(baseQuery, processPart.getStart(), processPart.getEnd());
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    super.addViewAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    dataForCommandKey.getConfiguration().setProcessPart(new ProcessPartDto());
  }
}
