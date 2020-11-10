/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by;

import com.google.common.collect.Sets;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.view.ViewPart;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.util.List;
import java.util.Optional;

public abstract class DistributedByPart<Data extends SingleReportDataDto> {

  @Setter
  protected ViewPart<Data> viewPart;

  public abstract Optional<Boolean> isKeyOfNumericType(final ExecutionContext<Data> context);

  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<Data> context) {
    viewPart.adjustSearchRequest(searchRequest, baseQuery, context);
  }

  public abstract AggregationBuilder createAggregation(final ExecutionContext<Data> context);

  public abstract List<DistributedByResult> retrieveResult(final SearchResponse response,
                                                           final Aggregations aggregations,
                                                           final ExecutionContext<Data> context);

  public void addDistributedByAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey) {
    addAdjustmentsForCommandKeyGeneration(dataForCommandKey);
    viewPart.addViewAdjustmentsForCommandKeyGeneration(dataForCommandKey);
  }

  public void enrichContextWithAllExpectedDistributedByKeys(
    final ExecutionContext<Data> context,
    final Aggregations aggregations) {
    context.setAllDistributedByKeys(Sets.newHashSet());
  }

  protected abstract void addAdjustmentsForCommandKeyGeneration(final Data dataForCommandKey);
}
