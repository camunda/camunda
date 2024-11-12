/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.DistributedByInterpreter;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public interface DistributedByInterpreterOS<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    extends DistributedByInterpreter<DATA, PLAN> {

  BoolQuery.Builder adjustQuery(
      final BoolQuery.Builder queryBuilder, final ExecutionContext<DATA, PLAN> context);

  void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<DATA, PLAN> context);

  Map<String, Aggregation> createAggregations(
      final ExecutionContext<DATA, PLAN> context, final Query baseQuery);

  List<DistributedByResult> retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<DATA, PLAN> context);

  default void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<DATA, PLAN> context, final Map<String, Aggregate> aggregations) {
    context.setAllDistributedByKeysAndLabels(new HashMap<>());
  }
}
