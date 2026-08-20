/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByInterpreterFacadeES
    extends AbstractInterpreterFacade<ProcessDistributedBy, ProcessDistributedByInterpreterES>
    implements ProcessDistributedByInterpreterES {

  public ProcessDistributedByInterpreterFacadeES(
      final List<ProcessDistributedByInterpreterES> interpreters) {
    super(interpreters, ProcessDistributedByInterpreterES::getSupportedDistributedBys);
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return interpretersMap.keySet();
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQueryBuilder) {
    return interpreter(context.getPlan().getDistributedBy())
        .createAggregations(context, baseQueryBuilder);
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
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
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    interpreter(context.getPlan().getDistributedBy())
        .adjustSearchRequest(searchRequestBuilder, baseQueryBuilder, context);
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
