/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


public abstract class GroupByPart<Data extends SingleReportDataDto> {

  @Setter
  @Getter
  protected DistributedByPart<Data> distributedByPart;

  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<Data> context) {
    distributedByPart.adjustSearchRequest(searchRequest, baseQuery, context);
  }

  public abstract List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                             final ExecutionContext<Data> context);

  public String generateCommandKey(final Supplier<Data> createNewDataDto) {
    final Data dataForCommandKey = createNewDataDto.get();
    addGroupByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    distributedByPart.addDistributedByAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    return dataForCommandKey.createCommandKey();
  }

  public CompositeCommandResult retrieveQueryResult(final SearchResponse response,
                                                    final ExecutionContext<Data> executionContext) {
    final CompositeCommandResult compositeCommandResult = new CompositeCommandResult(
      executionContext.getReportData(), distributedByPart.getViewPart().getViewProperty(executionContext)
    );
    executionContext.getReportConfiguration().getSorting().ifPresent(compositeCommandResult::setGroupBySorting);
    addQueryResult(compositeCommandResult, response, executionContext);
    return compositeCommandResult;
  }

  /**
   * This method returns the min and maximum values for range value types (e.g. number or date).
   * It defaults to an empty result and needs to get overridden when applicable.
   *
   * @param context   command execution context to perform the min max retrieval with
   * @param baseQuery filtering query on which data to perform the min max retrieval
   * @return min and max value range for the value grouped on by
   */
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<Data> context,
                                                final BoolQueryBuilder baseQuery) {
    return Optional.empty();
  }

  protected abstract String[] getIndexNames(ExecutionContext<Data> context);

  protected abstract void addQueryResult(final CompositeCommandResult compositeCommandResult,
                                         final SearchResponse response,
                                         final ExecutionContext<Data> executionContext);

  protected abstract void addGroupByAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey);

}
