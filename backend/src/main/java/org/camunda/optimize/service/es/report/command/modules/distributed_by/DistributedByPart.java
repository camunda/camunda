/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewFlowNodeDuration;
import org.camunda.optimize.service.es.report.command.modules.view.process.frequency.ProcessViewFlowNodeFrequency;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.HashMap;
import java.util.List;

public abstract class DistributedByPart<Data extends SingleReportDataDto> {

  @Setter
  @Getter
  protected ViewPart<Data> viewPart;

  public abstract boolean isKeyOfNumericType(final ExecutionContext<Data> context);

  public boolean isFlownodeReport() {
    return viewPart instanceof ProcessViewFlowNodeFrequency
      || viewPart instanceof ProcessViewFlowNodeDuration;
  }

  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<Data> context) {
    viewPart.adjustSearchRequest(searchRequest, baseQuery, context);
  }

  public abstract List<AggregationBuilder> createAggregations(final ExecutionContext<Data> context);

  public abstract List<DistributedByResult> retrieveResult(final SearchResponse response,
                                                           final Aggregations aggregations,
                                                           final ExecutionContext<Data> context);

  public abstract List<DistributedByResult> createEmptyResult(final ExecutionContext<Data> context);

  public void addDistributedByAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey) {
    addAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    viewPart.addViewAdjustmentsForCommandKeyGeneration(dataForCommandKey);
  }

  public void enrichContextWithAllExpectedDistributedByKeys(
    final ExecutionContext<Data> context,
    final Aggregations aggregations) {
    context.setAllDistributedByKeysAndLabels(new HashMap<>());
  }

  protected abstract void addAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey);
}
