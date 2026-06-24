/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDistributedByInterpreterFacadeOS
    extends AbstractInterpreterFacade<ProcessDistributedBy, ProcessDistributedByInterpreterOS>
    implements ProcessDistributedByInterpreterOS {

  public ProcessDistributedByInterpreterFacadeOS(
      final List<ProcessDistributedByInterpreterOS> interpreters) {
    super(interpreters, ProcessDistributedByInterpreterOS::getSupportedDistributedBys);
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return interpretersMap.keySet();
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return interpreter(context.getPlan().getDistributedBy()).createAggregations(context, baseQuery);
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan().getDistributedBy())
        .retrieveResult(response, aggregations, context);
  }

  @Override
  public List<DistributedByResult> createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan().getDistributedBy()).createEmptyResult(context);
  }

  @Override
  public Builder adjustQuery(
      final Builder queryBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan().getDistributedBy()).adjustQuery(queryBuilder, context);
  }

  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final Query baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    interpreter(context.getPlan().getDistributedBy())
        .adjustSearchRequest(searchRequestBuilder, baseQuery, context);
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, Aggregate> aggregations) {
    interpreter(context.getPlan().getDistributedBy())
        .enrichContextWithAllExpectedDistributedByKeys(context, aggregations);
  }

  @Override
  public boolean isKeyOfNumericType(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan().getDistributedBy()).isKeyOfNumericType(context);
  }
}
