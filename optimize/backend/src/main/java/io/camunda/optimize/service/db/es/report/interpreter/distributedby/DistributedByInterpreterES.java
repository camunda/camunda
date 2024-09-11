/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.DistributedByInterpreter;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface DistributedByInterpreterES<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    extends DistributedByInterpreter<DATA, PLAN> {
  void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<DATA, PLAN> context);

  Map<String, ContainerBuilder> createAggregations(
      final ExecutionContext<DATA, PLAN> context, final BoolQuery baseQuery);

  List<DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<DATA, PLAN> context);

  default void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<DATA, PLAN> context, final Map<String, Aggregate> aggregations) {
    context.setAllDistributedByKeysAndLabels(new HashMap<>());
  }
}
