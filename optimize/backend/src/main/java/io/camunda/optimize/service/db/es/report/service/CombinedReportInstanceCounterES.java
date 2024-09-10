/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.report.interpreter.plan.process.ProcessExecutionPlanInterpreterFacadeES;
import io.camunda.optimize.service.db.report.CombinedReportInstanceCounter;
import io.camunda.optimize.service.db.report.ExecutionContextFactory;
import io.camunda.optimize.service.db.report.ExecutionPlanExtractor;
import io.camunda.optimize.service.db.report.ReportEvaluationContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class CombinedReportInstanceCounterES extends CombinedReportInstanceCounter<QueryBuilder> {
  private final OptimizeElasticsearchClient esClient;
  private final ExecutionPlanExtractor executionPlanExtractor;
  private final ProcessExecutionPlanInterpreterFacadeES interpreter;

  @Override
  public long count(List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions) {
    final List<QueryBuilder> baseQueries = getAllBaseQueries(singleReportDefinitions);
    final QueryBuilder instanceCountRequestQuery = createInstanceCountRequestQueries(baseQueries);
    try {
      return esClient.count(new String[] {PROCESS_INSTANCE_MULTI_ALIAS}, instanceCountRequestQuery);
    } catch (IOException e) {
      final String message =
          String.format(
              "Could not count instances in combined report with single report IDs: [%s]",
              singleReportDefinitions.stream().map(ReportDefinitionDto::getId));
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    } catch (RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        log.info(
            "Could not evaluate combined instance count because no instance indices exist. "
                + "Returning a count of 0 instead.");
        return 0L;
      } else {
        throw e;
      }
    }
  }

  @Override
  protected ExecutionPlanExtractor getExecutionPlanExtractor() {
    return executionPlanExtractor;
  }

  @Override
  protected QueryBuilder getBaseQuery(
      ProcessExecutionPlan plan,
      ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> context) {
    return interpreter.getBaseQuery(ExecutionContextFactory.buildExecutionContext(plan, context));
  }

  private QueryBuilder createInstanceCountRequestQueries(final List<QueryBuilder> baseQueries) {
    final BoolQueryBuilder baseQuery = new BoolQueryBuilder();
    baseQueries.forEach(baseQuery::should);
    return baseQuery;
  }
}
