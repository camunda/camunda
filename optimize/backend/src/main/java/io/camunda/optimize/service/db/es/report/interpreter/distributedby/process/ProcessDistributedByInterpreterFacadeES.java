/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.AbstractInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByInterpreterFacadeES
    extends AbstractInterpreterFacade<ProcessDistributedBy, ProcessDistributedByInterpreterES>
    implements ProcessDistributedByInterpreterES {

  public ProcessDistributedByInterpreterFacadeES(
      List<ProcessDistributedByInterpreterES> interpreters) {
    super(interpreters, ProcessDistributedByInterpreterES::getSupportedDistributedBys);
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return interpretersMap.keySet();
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQueryBuilder) {
    return interpreter(context.getPlan().getDistributedBy())
        .createAggregations(context, baseQueryBuilder);
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      SearchResponse response,
      Aggregations aggregations,
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan().getDistributedBy())
        .retrieveResult(response, aggregations, context);
  }

  @Override
  public List<DistributedByResult> createEmptyResult(
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan().getDistributedBy()).createEmptyResult(context);
  }

  @Override
  public void adjustSearchRequest(
      SearchRequest searchRequest,
      BoolQueryBuilder baseQuery,
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    interpreter(context.getPlan().getDistributedBy())
        .adjustSearchRequest(searchRequest, baseQuery, context);
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Aggregations aggregations) {
    interpreter(context.getPlan().getDistributedBy())
        .enrichContextWithAllExpectedDistributedByKeys(context, aggregations);
  }

  @Override
  public boolean isKeyOfNumericType(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return interpreter(context.getPlan().getDistributedBy()).isKeyOfNumericType(context);
  }
}
