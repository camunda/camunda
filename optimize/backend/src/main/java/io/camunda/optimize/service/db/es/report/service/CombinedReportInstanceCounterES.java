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

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
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
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class CombinedReportInstanceCounterES
    extends CombinedReportInstanceCounter<BoolQuery.Builder> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CombinedReportInstanceCounterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ExecutionPlanExtractor executionPlanExtractor;
  private final ProcessExecutionPlanInterpreterFacadeES interpreter;

  public CombinedReportInstanceCounterES(
      final OptimizeElasticsearchClient esClient,
      final ExecutionPlanExtractor executionPlanExtractor,
      final ProcessExecutionPlanInterpreterFacadeES interpreter) {
    this.esClient = esClient;
    this.executionPlanExtractor = executionPlanExtractor;
    this.interpreter = interpreter;
  }

  @Override
  public long count(final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions) {
    final List<BoolQuery.Builder> baseQueries = getAllBaseQueries(singleReportDefinitions);
    final BoolQuery.Builder instanceCountRequestQuery =
        createInstanceCountRequestQueries(baseQueries);
    try {
      return esClient.count(new String[] {PROCESS_INSTANCE_MULTI_ALIAS}, instanceCountRequestQuery);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not count instances in combined report with single report IDs: [%s]",
              singleReportDefinitions.stream().map(ReportDefinitionDto::getId));
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        LOG.info(
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
  protected BoolQuery.Builder getBaseQuery(
      final ProcessExecutionPlan plan,
      final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> context) {
    return interpreter.getBaseQueryBuilder(
        ExecutionContextFactory.buildExecutionContext(plan, context));
  }

  private BoolQuery.Builder createInstanceCountRequestQueries(
      final List<BoolQuery.Builder> baseQueriesBuilders) {
    final BoolQuery.Builder baseQuery = new BoolQuery.Builder();
    baseQueriesBuilders.forEach(q -> baseQuery.should(s -> s.bool(q.build())));
    return baseQuery;
  }
}
