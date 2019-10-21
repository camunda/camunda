/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GroupByNone extends GroupByPart<NumberResultDto> {

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ProcessReportDataDto definitionData) {
    // nothing to do here, since we don't group so just pass the view part on
    return Arrays.asList(viewPart.createAggregation(definitionData));
  }

  @Override
  protected NumberResultDto retrieveResult(final SearchResponse response, final ProcessReportDataDto reportData) {
    NumberResultDto numberResultDto = new NumberResultDto();
    numberResultDto.setData(viewPart.retrieveResult(response.getAggregations(), reportData));
    numberResultDto.setInstanceCount(response.getHits().getTotalHits());
    return numberResultDto;
  }

  @Override
  protected void sortResultData(final ProcessReportDataDto reportData, final NumberResultDto resultDto) {
    // nothing to do here, because we should get a single result, which can't be sorted
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new NoneGroupByDto());
  }
}
