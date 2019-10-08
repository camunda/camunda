/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil;
import org.camunda.optimize.service.es.report.command.process.util.ProcessInstanceQueryUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.List;

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.createProcessEndDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.ProcessPartQueryUtil.processProcessPartAggregationOperations;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;


public class ProcessInstanceDurationGroupByEndDateWithProcessPartCommand
  extends AbstractProcessInstanceDurationGroupByDateCommand {

  public ProcessInstanceDurationGroupByEndDateWithProcessPartCommand(final AggregationStrategy strategy) {
    super(strategy);
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

  @Override
  public String getDateField() {
    return END_DATE;
  }

  @Override
  protected void addFiltersToQuery(final BoolQueryBuilder limitFilterQuery,
                                   final List<DateFilterDataDto> limitedFilters) {
    queryFilterEnhancer.getEndDateQueryFilter().addFilters(limitFilterQuery, limitedFilters);
  }

  @Override
  protected List<DateFilterDataDto> getReportDateFilter(final ProcessReportDataDto reportData) {
    return queryFilterEnhancer.extractFilters(reportData.getFilter(), EndDateFilterDto.class);
  }

  @Override
  protected BoolQueryBuilder createDefaultLimitingFilter(final GroupByDateUnit unit, final QueryBuilder query,
                                                         final ProcessReportDataDto reportData) {
    final BoolQueryBuilder limitFilterQuery;
    limitFilterQuery = createProcessEndDateHistogramBucketLimitingFilterFor(
      reportData.getFilter(),
      unit,
      configurationService.getEsAggregationBucketLimit(),
      ProcessInstanceQueryUtil.getLatestDate(query, getDateField(), esClient).orElse(null),
      queryFilterEnhancer
    );
    return limitFilterQuery;
  }
}
