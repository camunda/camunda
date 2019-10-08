/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.processProcessPartAggregationOperations;


public class ProcessInstanceDurationGroupByVariableWithProcessPartCommand
  extends AbstractProcessInstanceDurationByVariableCommand {

  public ProcessInstanceDurationGroupByVariableWithProcessPartCommand(AggregationStrategy strategy) {
    aggregationStrategy = strategy;
  }

  @Override
  public BoolQueryBuilder setupBaseQuery(ProcessReportDataDto processReportData) {
    BoolQueryBuilder boolQueryBuilder = super.setupBaseQuery(processReportData);
    ProcessPartDto processPart = processReportData.getConfiguration().getProcessPart()
      .orElseThrow(() -> new OptimizeRuntimeException("Missing ProcessPart"));
    return ProcessPartQueryUtil.addProcessPartQuery(
      boolQueryBuilder,
      processPart.getStart(),
      processPart.getEnd()
    );
  }

  @Override
  protected Long processAggregationOperation(Aggregations aggs) {
    return processProcessPartAggregationOperations(aggs, aggregationStrategy.getAggregationType());
  }

  @Override
  protected AggregationBuilder createOperationsAggregation() {
    ProcessPartDto processPart = ((ProcessReportDataDto) getReportData()).getConfiguration().getProcessPart()
      .orElseThrow(() -> new OptimizeRuntimeException("Missing ProcessPart"));
    return ProcessPartQueryUtil.createProcessPartAggregation(processPart.getStart(), processPart.getEnd());
  }
}
