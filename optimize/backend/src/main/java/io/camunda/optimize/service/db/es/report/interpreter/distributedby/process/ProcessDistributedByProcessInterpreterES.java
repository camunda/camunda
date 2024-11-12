/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_PROCESS;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.ProcessDistributedByProcessInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByProcessInterpreterES
    extends AbstractProcessDistributedByInterpreterES
    implements ProcessDistributedByProcessInterpreter {

  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  public ProcessDistributedByProcessInterpreterES(
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ConfigurationService configurationService,
      final ProcessDefinitionReader processDefinitionReader) {
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_PROCESS);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQueryBuilder) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.size(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .order(NamedValue.of("_key", SortOrder.Asc))
                        .missing(MISSING_TENANT_KEY)
                        .field(tenantField(context)));

    viewInterpreter
        .createAggregations(context)
        .forEach((k, v) -> builder.aggregations(k, v.build()));
    final Aggregation.Builder aggBuilder = new Aggregation.Builder();

    return Map.of(
        PROC_DEF_KEY_AGG,
        aggBuilder
            .terms(
                t ->
                    t.size(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .order(NamedValue.of("_key", SortOrder.Asc))
                        .field(definitionKeyField(context)))
            .aggregations(
                PROC_DEF_VERSION_AGG,
                Aggregation.of(
                    a ->
                        a.terms(
                                t ->
                                    t.size(
                                            configurationService
                                                .getElasticSearchConfiguration()
                                                .getAggregationBucketLimit())
                                        .order(NamedValue.of("_key", SortOrder.Asc))
                                        .field(definitionVersionField(context)))
                            .aggregations(TENANT_AGG, builder.build()))));
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<CompositeCommandResult.DistributedByResult> results = new ArrayList<>();
    final Map<String, List<ProcessBucket>> bucketsByDefKey =
        extractBucketsByDefKey(response, aggregations, context);
    return retrieveResult(bucketsByDefKey, context);
  }

  @Override
  public CompositeCommandResult.ViewResult emptyViewResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return viewInterpreter.createEmptyResult(context);
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return emptyResult(context);
  }

  private Map<String, List<ProcessBucket>> extractBucketsByDefKey(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, List<ProcessBucket>> bucketsByDefKey = new HashMap<>();
    final StringTermsAggregate procDefKeyAgg = aggregations.get(PROC_DEF_KEY_AGG).sterms();
    if (procDefKeyAgg != null) {
      for (final co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket keyBucket :
          procDefKeyAgg.buckets().array()) {
        final Aggregate procDefVersionAgg = keyBucket.aggregations().get(PROC_DEF_VERSION_AGG);
        if (procDefVersionAgg != null) {
          for (final StringTermsBucket versionBucket :
              procDefVersionAgg.sterms().buckets().array()) {
            final Aggregate tenantTermsAgg = versionBucket.aggregations().get(TENANT_AGG);
            if (tenantTermsAgg != null) {
              final List<ProcessBucket> bucketsForKey =
                  tenantTermsAgg.sterms().buckets().array().stream()
                      .map(
                          tenantBucket ->
                              new ProcessBucket(
                                  keyBucket.key().stringValue(),
                                  versionBucket.key().stringValue(),
                                  tenantBucket.key().stringValue(),
                                  tenantBucket.docCount(),
                                  viewInterpreter.retrieveResult(
                                      response, tenantBucket.aggregations(), context)))
                      .collect(Collectors.toList());
              bucketsByDefKey
                  .computeIfAbsent(keyBucket.key().stringValue(), key -> new ArrayList<>())
                  .addAll(bucketsForKey);
            }
          }
        }
      }
    }
    return bucketsByDefKey;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }

  @Override
  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }
}
