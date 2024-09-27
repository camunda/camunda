/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.DistributedByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.ViewInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractGroupByInterpreterOS<
    DATA extends SingleReportDataDto, PLAN extends ExecutionPlan> {

  public BoolQuery.Builder adjustQuery(
      final BoolQuery.Builder queryBuilder, final ExecutionContext<DATA, PLAN> context) {
    return getDistributedByInterpreter().adjustQuery(queryBuilder, context);
  }

  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<DATA, PLAN> context) {
    getDistributedByInterpreter().adjustSearchRequest(searchRequestBuilder, baseQuery, context);
  }

  public abstract Map<String, Aggregation> createAggregation(
      final Query baseQuery, final ExecutionContext<DATA, PLAN> context);

  public CompositeCommandResult retrieveQueryResult(
      final SearchResponse<RawResult> response,
      final ExecutionContext<DATA, PLAN> executionContext) {
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
      final SearchResponse<RawResult> response,
      final ExecutionContext<DATA, PLAN> executionContext);

  protected abstract DistributedByInterpreterOS<DATA, PLAN> getDistributedByInterpreter();

  protected abstract ViewInterpreterOS<DATA, PLAN> getViewInterpreter();
}
