/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.DatabaseConstants.VERSION_BASELINE_AGGREGATION;
import static java.util.stream.Collectors.toMap;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.GenericProcessExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class GenericProcessExecutionPlanInterpreterES
    extends AbstractProcessExecutionPlanInterpreterES
    implements GenericProcessExecutionPlanInterpreter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(GenericProcessExecutionPlanInterpreterES.class);
  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ConfigurationService configurationService;

  public GenericProcessExecutionPlanInterpreterES(
      final ProcessDefinitionReader processDefinitionReader,
      final OptimizeElasticsearchClient esClient,
      final ProcessQueryFilterEnhancerES queryFilterEnhancer,
      final ProcessGroupByInterpreterFacadeES groupByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ConfigurationService configurationService) {
    this.processDefinitionReader = processDefinitionReader;
    this.esClient = esClient;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
  }

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }

  public OptimizeElasticsearchClient getEsClient() {
    return this.esClient;
  }

  public ProcessQueryFilterEnhancerES getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public ProcessGroupByInterpreterFacadeES getGroupByInterpreter() {
    return this.groupByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }

  public ConfigurationService getConfigurationService() {
    return this.configurationService;
  }

  @Override
  protected Map<String, Long> retrievePerGroupBaselineCounts(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final String[] indices) {
    if (!(getGroupByInterpreter()
        instanceof final ProcessGroupByInterpreterES groupByInterpreter)) {
      return Map.of();
    }
    final Optional<String> baselineField =
        groupByInterpreter.getBaselineCountAggregationField(context);
    if (baselineField.isEmpty()) {
      return Map.of();
    }
    final String field = baselineField.get();
    final BoolQuery.Builder baselineQuery = setupUnfilteredBaseQueryBuilder(context);
    final ResponseBody<?> response;
    try {
      response =
          getEsClient()
              .search(
                  OptimizeSearchRequestBuilderES.of(
                      r ->
                          r.optimizeIndex(getEsClient(), indices)
                              .query(q -> q.bool(baselineQuery.build()))
                              .size(0)
                              .aggregations(
                                  VERSION_BASELINE_AGGREGATION,
                                  a ->
                                      a.terms(
                                          t ->
                                              t.field(field)
                                                  .size(
                                                      getConfigurationService()
                                                          .getElasticSearchConfiguration()
                                                          .getAggregationBucketLimit())
                                                  .order(NamedValue.of("_key", SortOrder.Asc))))),
                  Object.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not retrieve per-version baseline counts", e);
    }
    return response
        .aggregations()
        .get(VERSION_BASELINE_AGGREGATION)
        .sterms()
        .buckets()
        .array()
        .stream()
        .collect(toMap(b -> b.key().stringValue(), StringTermsBucket::docCount));
  }
}
