/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.view;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

public abstract class ViewPart<Data extends SingleReportDataDto> {

  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<Data> context) {
    // by default don't do anything
  }

  public abstract ViewProperty getViewProperty(final ExecutionContext<Data> context);

  public abstract AggregationBuilder createAggregation(final ExecutionContext<Data> context);

  public abstract ViewResult retrieveResult(final SearchResponse response,
                                            final Aggregations aggs,
                                            final ExecutionContext<Data> context);

  public abstract void addViewAdjustmentsForCommandKeyGeneration(Data dataForCommandKey);
}
