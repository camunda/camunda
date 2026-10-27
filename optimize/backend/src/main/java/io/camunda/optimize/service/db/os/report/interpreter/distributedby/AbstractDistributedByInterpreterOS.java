/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.view.ViewInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;

public abstract class AbstractDistributedByInterpreterOS<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    implements DistributedByInterpreterOS<DATA, PLAN> {

  protected abstract ViewInterpreterOS<DATA, PLAN> getViewInterpreter();

  @Override
  public BoolQuery.Builder adjustQuery(
      final BoolQuery.Builder queryBuilder, final ExecutionContext<DATA, PLAN> context) {
    return getViewInterpreter().adjustQuery(queryBuilder, context);
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<DATA, PLAN> context) {
    getViewInterpreter().adjustSearchRequest(searchRequestBuilder, baseQuery, context);
  }
}
