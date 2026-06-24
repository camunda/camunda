/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.DistributedByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.ViewInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractGroupByInterpreterES<
    DATA extends SingleReportDataDto, PLAN extends ExecutionPlan> {
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<DATA, PLAN> context) {
    getDistributedByInterpreter()
        .adjustSearchRequest(searchRequestBuilder, baseQueryBuilder, context);
  }

  public abstract Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery, final ExecutionContext<DATA, PLAN> context);

  public CompositeCommandResult retrieveQueryResult(
      final ResponseBody<?> response, final ExecutionContext<DATA, PLAN> executionContext) {
    final CompositeCommandResult compositeCommandResult =
        new CompositeCommandResult(
            executionContext.getReportData(),
            getViewInterpreter().getViewProperty(executionContext));
    executionContext
        .getReportConfiguration()
        .getSorting()
        .ifPresent(compositeCommandResult::setGroupBySorting);
    addQueryResult(compositeCommandResult, response, executionContext);
    return compositeCommandResult;
  }

  /**
   * This method returns the min and maximum values for range value types (e.g. number or date). It
   * defaults to an empty result and needs to get overridden when applicable.
   *
   * @param context command execution context to perform the min max retrieval with
   * @param baseQuery filtering query on which data to perform the min max retrieval
   * @return min and max value range for the value grouped on by
   */
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<DATA, PLAN> context, final Query baseQuery) {
    return Optional.empty();
  }

  protected abstract String[] getIndexNames(ExecutionContext<DATA, PLAN> context);

  protected abstract void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<DATA, PLAN> executionContext);

  protected abstract DistributedByInterpreterES<DATA, PLAN> getDistributedByInterpreter();

  protected abstract ViewInterpreterES<DATA, PLAN> getViewInterpreter();
}
