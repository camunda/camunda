/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;


public abstract class GroupByPart<R extends SingleReportResultDto> {

  @Setter
  protected ViewPart viewPart;

  public abstract AggregationBuilder createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                       final ProcessReportDataDto definitionData);

  public R retrieveQueryResult(final SearchResponse response,
                               final ProcessReportDataDto reportData) {
    final R result = retrieveQueryResult(response);
    sortResultData(reportData, result);
    return result;
  }

  protected abstract R retrieveQueryResult(final SearchResponse response);

  protected abstract void sortResultData(final ProcessReportDataDto reportData, final R resultDto);

}
