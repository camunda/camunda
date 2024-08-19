/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.ViewPart;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.duration.ProcessViewFlowNodeDuration;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.frequency.ProcessViewFlowNodeFrequency;
import java.util.HashMap;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

public abstract class DistributedByPart<Data extends SingleReportDataDto> {

  protected ViewPart<Data> viewPart;

  public abstract boolean isKeyOfNumericType(final ExecutionContext<Data> context);

  public boolean isFlownodeReport() {
    return viewPart instanceof ProcessViewFlowNodeFrequency
        || viewPart instanceof ProcessViewFlowNodeDuration;
  }

  public void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<Data> context) {
    viewPart.adjustSearchRequest(searchRequest, baseQuery, context);
  }

  public abstract List<AggregationBuilder> createAggregations(final ExecutionContext<Data> context);

  public abstract List<DistributedByResult> retrieveResult(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<Data> context);

  public abstract List<DistributedByResult> createEmptyResult(final ExecutionContext<Data> context);

  public void addDistributedByAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey) {
    addAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    viewPart.addViewAdjustmentsForCommandKeyGeneration(dataForCommandKey);
  }

  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<Data> context, final Aggregations aggregations) {
    context.setAllDistributedByKeysAndLabels(new HashMap<>());
  }

  protected abstract void addAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey);

  public ViewPart<Data> getViewPart() {
    return viewPart;
  }

  public void setViewPart(final ViewPart<Data> viewPart) {
    this.viewPart = viewPart;
  }
}
