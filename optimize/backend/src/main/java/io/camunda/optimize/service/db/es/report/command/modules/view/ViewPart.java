/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

public abstract class ViewPart<Data extends SingleReportDataDto> {

  public void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<Data> context) {
    // by default don't do anything
  }

  public abstract ViewProperty getViewProperty(final ExecutionContext<Data> context);

  public abstract List<AggregationBuilder> createAggregations(final ExecutionContext<Data> context);

  public abstract ViewResult retrieveResult(
      final SearchResponse response, final Aggregations aggs, final ExecutionContext<Data> context);

  public abstract void addViewAdjustmentsForCommandKeyGeneration(Data dataForCommandKey);

  public abstract ViewResult createEmptyResult(ExecutionContext<Data> context);
}
