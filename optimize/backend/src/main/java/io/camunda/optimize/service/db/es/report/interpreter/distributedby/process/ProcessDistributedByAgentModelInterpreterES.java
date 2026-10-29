/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_AGENT_MODEL;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Splits agent runs by the model of the run, e.g. tokens per run over time with one line per model.
 * Every group gets every model seen in the report, empty where a model had no runs, so the lines of
 * a chart line up.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByAgentModelInterpreterES
    extends AbstractProcessDistributedByInterpreterES {

  static final String MODEL_AGGREGATION = "agentRunModel";
  static final String MISSING_MODEL_KEY = "unknown";

  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ConfigurationService configurationService;

  public ProcessDistributedByAgentModelInterpreterES(
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ConfigurationService configurationService) {
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_AGENT_MODEL);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQuery) {
    final Aggregation.Builder.ContainerBuilder byModel =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.field(AgentRunIndex.MODEL)
                        .missing(MISSING_MODEL_KEY)
                        .size(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit()));
    viewInterpreter
        .createAggregations(context)
        .forEach((k, v) -> byModel.aggregations(k, v.build()));
    return Map.of(MODEL_AGGREGATION, byModel);
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, DistributedByResult> byModel = new TreeMap<>();
    final Aggregate models = aggregations.get(MODEL_AGGREGATION);
    if (models != null) {
      for (final StringTermsBucket bucket : models.sterms().buckets().array()) {
        final String model = bucket.key().stringValue();
        byModel.put(
            model,
            createDistributedByResult(
                model,
                model,
                viewInterpreter.retrieveResult(response, bucket.aggregations(), context)));
      }
    }
    final Map<String, String> expected = context.getAllDistributedByKeysAndLabels();
    if (expected != null) {
      expected.forEach(
          (model, label) ->
              byModel.computeIfAbsent(
                  model,
                  key ->
                      createDistributedByResult(
                          key, label, viewInterpreter.createEmptyResult(context))));
    }
    return new ArrayList<>(byModel.values());
  }

  /** Collects the models of all groups, looking through the group-by's buckets. */
  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, Aggregate> aggregations) {
    final Map<String, String> models = new TreeMap<>();
    collectModels(aggregations, models);
    context.setAllDistributedByKeysAndLabels(models);
  }

  private static void collectModels(
      final Map<String, Aggregate> aggregations, final Map<String, String> models) {
    aggregations.forEach(
        (name, aggregate) -> {
          if (MODEL_AGGREGATION.equals(name) && aggregate.isSterms()) {
            aggregate
                .sterms()
                .buckets()
                .array()
                .forEach(
                    bucket -> models.put(bucket.key().stringValue(), bucket.key().stringValue()));
          } else if (aggregate.isDateHistogram()) {
            aggregate
                .dateHistogram()
                .buckets()
                .array()
                .forEach(bucket -> collectModels(bucket.aggregations(), models));
          } else if (aggregate.isDateRange()) {
            aggregate
                .dateRange()
                .buckets()
                .array()
                .forEach(bucket -> collectModels(bucket.aggregations(), models));
          } else if (aggregate.isFilter()) {
            collectModels(aggregate.filter().aggregations(), models);
          } else if (aggregate.isSterms()) {
            aggregate
                .sterms()
                .buckets()
                .array()
                .forEach(bucket -> collectModels(bucket.aggregations(), models));
          }
        });
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }
}
