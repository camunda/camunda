/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.DatabaseConstants.VERSION_BASELINE_AGGREGATION;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.GenericProcessExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class GenericProcessExecutionPlanInterpreterOS
    extends AbstractProcessExecutionPlanInterpreterOS
    implements GenericProcessExecutionPlanInterpreter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(GenericProcessExecutionPlanInterpreterOS.class);
  private final ProcessDefinitionReader processDefinitionReader;
  private final OptimizeOpenSearchClient osClient;
  private final ProcessQueryFilterEnhancerOS queryFilterEnhancer;
  private final ProcessGroupByInterpreterFacadeOS groupByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final ConfigurationService configurationService;

  public GenericProcessExecutionPlanInterpreterOS(
      final ProcessDefinitionReader processDefinitionReader,
      final OptimizeOpenSearchClient osClient,
      final ProcessQueryFilterEnhancerOS queryFilterEnhancer,
      final ProcessGroupByInterpreterFacadeOS groupByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final ConfigurationService configurationService) {
    this.processDefinitionReader = processDefinitionReader;
    this.osClient = osClient;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
  }

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }

  public OptimizeOpenSearchClient getOsClient() {
    return this.osClient;
  }

  public ProcessQueryFilterEnhancerOS getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }

  public ProcessGroupByInterpreterFacadeOS getGroupByInterpreter() {
    return this.groupByInterpreter;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }

  public ConfigurationService getConfigurationService() {
    return this.configurationService;
  }

  @Override
  protected Map<String, Long> retrievePerGroupBaselineCounts(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final String[] indices) {
    final Optional<String> baselineField =
        getGroupByInterpreter().getBaselineCountAggregationField(context);
    if (baselineField.isEmpty()) {
      return Map.of();
    }
    final String field = baselineField.get();
    final BoolQuery.Builder baselineQuery = unfilteredBaseQueryBuilder(context);
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(Arrays.asList(indices))
            .query(baselineQuery.build().toQuery())
            .size(0)
            .aggregations(
                VERSION_BASELINE_AGGREGATION,
                a ->
                    a.terms(
                        t ->
                            t.field(field)
                                .size(
                                    getConfigurationService()
                                        .getOpenSearchConfiguration()
                                        .getAggregationBucketLimit())
                                .order(Map.of("_key", SortOrder.Asc))));
    final SearchResponse<?> response =
        getOsClient()
            .searchWithFixedAggregations(
                requestBuilder, Object.class, "Could not retrieve per-version baseline counts");
    final StringTermsAggregate versionsAgg =
        response.aggregations().get(VERSION_BASELINE_AGGREGATION).sterms();
    return versionsAgg.buckets().array().stream().collect(toMap(b -> b.key(), b -> b.docCount()));
  }
}
