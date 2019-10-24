/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


public abstract class GroupByPart<Data extends SingleReportDataDto> {

  @Setter
  protected DistributedByPart<Data> distributedByPart;

  public void adjustBaseQuery(final BoolQueryBuilder baseQuery, final Data definitionData) {
    distributedByPart.adjustBaseQuery(baseQuery, definitionData);
  }

  public abstract List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                             final Data definitionData);

  public String generateCommandKey(final Supplier<Data> createNewDataDto) {
    final Data dataForCommandKey = createNewDataDto.get();
    addGroupByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    distributedByPart.addDistributedByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    return dataForCommandKey.createCommandKey();
  }

  public abstract CompositeCommandResult retrieveQueryResult(SearchResponse response, Data reportData);

  public boolean getSortByKeyIsOfNumericType(final Data definitionData) {
    return false;
  }

  public Optional<SortingDto> getSorting(final Data definitionData) {
    return definitionData.getConfiguration().getSorting();
  }

  protected abstract void addGroupByAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey);

}
