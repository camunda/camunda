/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.DistributedByInterpreter;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.HashMap;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

public interface DistributedByInterpreterES<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    extends DistributedByInterpreter<DATA, PLAN> {
  void adjustSearchRequest(
      final SearchRequest searchRequest,
      final BoolQueryBuilder baseQuery,
      final ExecutionContext<DATA, PLAN> context);

  List<AggregationBuilder> createAggregations(
      final ExecutionContext<DATA, PLAN> context, final QueryBuilder baseQueryBuilder);

  List<DistributedByResult> retrieveResult(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<DATA, PLAN> context);

  default void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<DATA, PLAN> context, final Aggregations aggregations) {
    context.setAllDistributedByKeysAndLabels(new HashMap<>());
  }
}
