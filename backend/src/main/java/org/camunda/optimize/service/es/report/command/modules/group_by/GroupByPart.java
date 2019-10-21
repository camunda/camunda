/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.List;


public abstract class GroupByPart<R extends SingleReportResultDto> {

  @Setter
  protected ViewPart viewPart;

  public void adjustBaseQuery(final BoolQueryBuilder baseQuery, final ProcessReportDataDto definitionData) {
    viewPart.adjustBaseQuery(baseQuery, definitionData);
  }

  public abstract List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                             final ProcessReportDataDto definitionData);

  public R retrieveQueryResult(final SearchResponse response,
                               final ProcessReportDataDto reportData) {
    final R result = retrieveResult(response, reportData);
    sortResultData(reportData, result);
    return result;
  }

  protected abstract R retrieveResult(final SearchResponse response, final ProcessReportDataDto reportData);

  protected abstract void sortResultData(final ProcessReportDataDto reportData, final R resultDto);

  public String generateCommandKey() {
    final ProcessReportDataDto dataForCommandKey = new ProcessReportDataDto();
    addGroupByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    viewPart.addViewAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    return dataForCommandKey.createCommandKey();
  }

  protected abstract void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey);

}
