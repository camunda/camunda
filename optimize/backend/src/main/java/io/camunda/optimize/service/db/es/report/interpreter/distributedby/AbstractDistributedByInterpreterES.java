/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.ViewInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;

public abstract class AbstractDistributedByInterpreterES<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    implements DistributedByInterpreterES<DATA, PLAN> {

  protected abstract ViewInterpreterES<DATA, PLAN> getViewInterpreter();

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<DATA, PLAN> context) {
    getViewInterpreter().adjustSearchRequest(searchRequestBuilder, baseQueryBuilder, context);
  }
}
