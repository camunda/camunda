/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.List;
import java.util.Optional;


public abstract class GroupByPart {

  @Setter
  protected DistributedByPart distributedByPart;

  public void adjustBaseQuery(final BoolQueryBuilder baseQuery, final ProcessReportDataDto definitionData) {
    distributedByPart.adjustBaseQuery(baseQuery, definitionData);
  }

  public abstract List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                             final ProcessReportDataDto definitionData);

  public abstract CompositeCommandResult retrieveQueryResult(SearchResponse response, ProcessReportDataDto reportData);

  public String generateCommandKey() {
    final ProcessReportDataDto dataForCommandKey = new ProcessReportDataDto();
    addGroupByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    distributedByPart.addDistributedByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    return dataForCommandKey.createCommandKey();
  }

  public boolean getSortByKeyIsOfNumericType(final ProcessReportDataDto definitionData) {
    return false;
  }

  public Optional<SortingDto> getSorting(final ProcessReportDataDto definitionData) {
    return definitionData.getConfiguration().getSorting();
  }

  protected abstract void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey);

}
